package tech.gdragon.listener

import com.squareup.tape.QueueFile
import de.sciss.jump3r.lowlevel.LameEncoder
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.audio.UserAudio
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.VoiceChannel
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import synapticloop.b2.B2ApiClient
import tech.gdragon.BotUtils
import tech.gdragon.db.dao.Guild
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CombinedAudioRecorderHandler(val volume: Double, val voiceChannel: VoiceChannel) : AudioReceiveHandler {
  companion object {
    private const val AFK_LIMIT = (2 * 60 * 1000) / 20                      // 2 minutes in ms over 20ms increments
    private const val MAX_RECORDING_MB = 110
    private const val MAX_RECORDING_SIZE = MAX_RECORDING_MB * 1024 * 1024   // 8MB
    private const val BUFFER_TIMEOUT = 200L                                 // 200 milliseconds
    private const val BUFFER_MAX_COUNT = 8
    private const val BITRATE = 128                                         // 128 kpbs
    private const val BYTES_PER_SECOND = 16_000L                            // 128 kbps == 16000 bytes per second
  }

  private val logger = LoggerFactory.getLogger(this.javaClass)
  val bucketId: String = System.getenv("B2_BUCKET_ID") ?: ""
  val bucketName: String = System.getenv("B2_BUCKET_NAME") ?: ""

  // State-licious
  private var subject: Subject<CombinedAudio>? = null
  private var subscription: Disposable? = null
  private var uuid: UUID? = null
  private var queueFile: QueueFile? = null

  private var canReceive = true
  private var afkCounter = 0

  private var filename: String? = null
  private var queueFilename: String? = null
  private var recordingSize: Int = 0

  private var b2Client: B2ApiClient? = null

  init {
    subscription = createRecording()

    val accountId: String = System.getenv("B2_ACCOUNT_ID") ?: ""
    val accountKey: String = System.getenv("B2_APP_KEY") ?: ""

    if (accountId.isNotBlank() && accountKey.isNotBlank() && bucketId.isNotBlank()) {
      b2Client = B2ApiClient(accountId, accountKey)
    } else {
      logger.warn("""|B2 client is not configured correctly, missing information.
                     |accountId: {}
                     |accountKey: {}
                     |bucketId: {}
                     |bucketName: {}
                     |""".trimMargin(), accountId, accountKey, bucketId, bucketName)
    }
  }

  /**
   * Checks if everyone in voice chat is afk. Super malformed function as it has
   * side effects and triggers messages outside of the scope
   */
  private fun isAfk(userCount: Int): Boolean {
    if (userCount == 0) afkCounter++ else afkCounter = 0

    val isAfk = afkCounter >= AFK_LIMIT

    if (isAfk) {
      logger.info("AFK detected, leaving '{}' voice channel in {}", voiceChannel.name, voiceChannel.guild.name)
      transaction {
        Guild.findById(voiceChannel.guild.idLong)?.settings?.defaultTextChannel
      }?.let {
        val textChannel = voiceChannel.guild.getTextChannelById(it)
        BotUtils.sendMessage(textChannel, "No audio for 2 minutes, leaving from AFK detection...")
      }

      thread(start = true) {
        BotUtils.leaveVoiceChannel(voiceChannel)
      }
    }

    return isAfk
  }

  private fun createRecording(): Disposable? {
    subject = PublishSubject.create()
    uuid = UUID.randomUUID()
    filename = "recordings/$uuid.mp3"
    queueFilename = "recordings/$uuid.queue"
    queueFile = QueueFile(File(queueFilename))
    canReceive = true

    val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, BITRATE, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false)


    return subject
      ?.map { it.getAudioData(volume) }
      ?.buffer(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS, BUFFER_MAX_COUNT)
      ?.flatMap({ bytesArray ->
        val baos = ByteArrayOutputStream()

        bytesArray.forEach {
          val buffer = ByteArray(it.size)
          val bytesEncoded = encoder.encodeBuffer(it, 0, it.size, buffer)
          baos.write(buffer, 0, bytesEncoded)
        }

        Observable.fromArray(baos.toByteArray())
      })
      ?.collectInto(queueFile!!, { queue, bytes ->

        while (recordingSize + bytes.size > MAX_RECORDING_SIZE) {
          recordingSize -= queue.peek()?.size ?: 0
          queue.remove()
        }
        queue.add(bytes)
        recordingSize += bytes.size
      })
      ?.subscribe({ _, e ->
        e?.let {
          logger.error("An error occurred in the recording pipeline.", it)
        }
      })
  }

  fun saveRecording(voiceChannel: VoiceChannel?, textChannel: TextChannel?) {
    canReceive = false
    subscription?.dispose()

    val recording = File(filename)

    FileOutputStream(recording).use {
      queueFile?.apply {
        forEach({ stream, _ ->
          stream.transferTo(it)
        })

        clear()
        close()
        File(queueFilename).delete()
      }
    }

    val recordingSizeInMB = recording.length().toDouble() / 1024 / 1024

    logger.info("Saving audio file '{}' from {} on {} of size {} MB.",
      recording.name, voiceChannel?.name, voiceChannel?.guild?.name, recordingSizeInMB)
    logger.debug("Recording size in bytes: {}", recordingSize)

    uploadRecording(recording, recordingSizeInMB, voiceChannel, textChannel)

    // Resume recording
    subscription = createRecording()
  }

  fun saveClip(seconds: Long, voiceChannel: VoiceChannel?, channel: TextChannel?) {
    // Stop recording so that we can copy Queue File
    canReceive = false

    val path = Paths.get(queueFilename)
    val clipPath = Paths.get("recordings/clip-${UUID.randomUUID()}.queue")

    // Copy the original Queue File so that we can resume receiving audio
    Files.copy(path, clipPath, StandardCopyOption.REPLACE_EXISTING)
    canReceive = true

    val queueFile = QueueFile(clipPath.toFile())
    val recording = File(clipPath.toString().replace("queue", "mp3"))
    var clipRecordingSize = recordingSize.toLong()

    // Reduce the queue size until it's just over the expected clip size
    while (clipRecordingSize - queueFile.peek().size > BYTES_PER_SECOND * seconds) {
      queueFile.remove()
      clipRecordingSize -= queueFile.peek().size
    }

    FileOutputStream(recording).use {
      queueFile.apply {
        forEach({ stream, _ ->
          stream.transferTo(it)
        })

        close()
        Files.delete(clipPath)
      }
    }

    val recordingSizeInMB = recording.length().toDouble() / 1024 / 1024
    logger.info("Saving audio file '{}' from {} on {} of size {} MB.",
      recording.name, voiceChannel, channel?.guild?.name, recordingSizeInMB)

    uploadRecording(recording, recordingSizeInMB, voiceChannel, channel)
  }

  private fun uploadRecording(recording: File, recordingSize: Double, voiceChannel: VoiceChannel?, channel: TextChannel?) {
    val guildName = channel?.guild?.name

    if (recording.length() < MAX_RECORDING_SIZE) {

      if (bucketId.isNotBlank()) {
        val b2ClientUrl = b2Client?.downloadUrl
        val b2Filename = "${channel?.guild?.id}/${recording.name}"

        logger.info("Preparing to upload recording to: {}", "$b2ClientUrl/file/$b2Filename")

        val result = b2Client?.uploadFile(bucketId, b2Filename, recording)

        logger.info("Finished uploading file: {}", result?.fileName)

        val recordingBaseUrl = (System.getenv("RECORDING_BASE_URL") ?: "$b2ClientUrl/file") + "/$bucketName"
        val recordingUrl = "$recordingBaseUrl/$b2Filename"

        val message = """|Recording for <#${voiceChannel?.id}> in `$guildName`.
                         |:microphone2: $recordingUrl
                         |
                         |_Unfortunately, current recordings are limited to the last ${MAX_RECORDING_MB}MB recorded._
                         |""".trimMargin()

        BotUtils.sendMessage(channel, message)

        cleanup(recording)
      } else {
        logger.warn("B2 Bucket ID not set.")
      }
    } else {
      BotUtils.sendMessage(channel, "Could not upload, file too large: " + recordingSize + "MB.")
    }
  }

  fun disconnect() {
    canReceive = false
    subject?.onComplete()
    subscription?.dispose()
    queueFile?.apply {
      clear()
      close()
      File(queueFilename).delete()
    }

  }

  private fun cleanup(recording: File) {
    val isDeleteSuccess = recording.delete()
    logger.info("Deleting file {}...", recording.name)

    if (isDeleteSuccess)
      logger.info("Successfully deleted file {}.", recording.name)
    else
      logger.error("Could not delete file {}.", recording.name)
  }

  override fun canReceiveUser(): Boolean = false

  override fun canReceiveCombined(): Boolean = canReceive

  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    if (!isAfk(combinedAudio.users.size)) {
      subject?.onNext(combinedAudio)
    }
  }

  override fun handleUserAudio(userAudio: UserAudio?) = TODO("Not implemented.")
}
