package tech.gdragon.data

import io.minio.*
import mu.KotlinLogging
import net.jodah.failsafe.Failsafe
import net.jodah.failsafe.RetryPolicy
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.io.ByteArrayInputStream
import java.io.File
import java.time.temporal.ChronoUnit
import java.util.*

interface Datastore {
  fun upload(key: String, file: File): UploadResult
}

class LocalDatastore(localBucket: String) : Datastore {
  init {
      TODO("ensure that localBucket directory is created")
  }

  override fun upload(key: String, file: File): UploadResult {
    TODO("Not yet implemented")
  }
}

class RemoteDatastore(
  accessKey: String,
  val bucketName: String,
  endpoint: String,
  secretKey: String,
  val baseUrl: String
) : Datastore {
  val logger = KotlinLogging.logger { }

  private val client: MinioClient = MinioClient
    .builder()
    .endpoint(endpoint)
    .credentials(accessKey, secretKey)
    .build()

  private val retryPolicy: RetryPolicy<Unit> = RetryPolicy<Unit>()
    .withBackoff(2, 30, ChronoUnit.SECONDS)
    .withJitter(.25)
    .onRetry { ex -> logger.warn { "Failure #${ex.attemptCount}. Retrying!" } }

  init {
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
      logger.warn {
        "$bucketName bucket does not exist! Creating..."
      }
      client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
    }
  }

  override fun upload(key: String, file: File): UploadResult {
    logger.info {
      "Uploading: $baseUrl/$key"
    }

    Failsafe.with(retryPolicy).run { ->
      ByteArrayInputStream(file.readBytes()).use { bais ->
        val putObjectArgs = PutObjectArgs
          .builder()
          .bucket(bucketName)
          .`object`(key)
          .stream(bais, bais.available().toLong(), -1)
          .build()
        client.putObject(putObjectArgs)
      }
    }

    val statObjectArgs = StatObjectArgs
      .builder()
      .bucket(bucketName)
      .`object`(key)
      .build()
    val stat = UploadResult.from(baseUrl, client.statObject(statObjectArgs))

    logger.info {
      "Finished uploading file - (${FileUtils.byteCountToDisplaySize(stat.size)}) ${stat.key}"
    }

    return stat
  }
}

data class UploadResult(val key: String, val timestamp: DateTime, val size: Long, val url: String) {
  companion object {
    fun from(baseUrl: String, stat: StatObjectResponse): UploadResult {
      val createdTime = stat.lastModified().toInstant().toEpochMilli()
      val tz = DateTimeZone.forTimeZone(TimeZone.getTimeZone(stat.lastModified().zone))

      return UploadResult(stat.`object`(), DateTime(createdTime, tz), stat.size(), "$baseUrl/${stat.`object`()}")
    }
  }
}
