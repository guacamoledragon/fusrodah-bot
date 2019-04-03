package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import net.dv8tion.jda.core.entities.Channel as DiscordChannel

class AutoStop : CommandHandler {
  private val warningMessage = "\n\n:warning: _`autostop` is still a very new feature, so if you encounter any issues please " +
    "report in the support server.\n**REMEMBER**: `autostop` does not imply `autosave`!_"

  private fun updateChannelAutoStop(channel: DiscordChannel, autoStop: Int?) {
    transaction {
      val guild = channel.guild.run {
        Guild.findOrCreate(idLong, name, region.name)
      }

      Channel
        .findOrCreate(channel.idLong, channel.name, guild)
        .also { it.autoStop = autoStop }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int? = when (args.last()) {
          "off" -> null
          "0" -> null
          else -> {
            val lastArg = args.last().toInt()

            if (lastArg < 0) {
              throw InvalidCommand(::usage, "Number must be positive: $lastArg")
            } else {
              lastArg
            }
          }
        }

        if (channelName == "all") {
          val channels = event.guild.voiceChannels
          channels.forEach { updateChannelAutoStop(it, number) }

          if (number != null) {
            "Will now automatically leave any voice channel with **$number** or less people."
          } else {
            "Will no longer automatically stop recording any channel."
          }
        } else {
          val channels = event.guild.getVoiceChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            "Cannot find voice channel `$channelName`."
          } else {
            channels.forEach { updateChannelAutoStop(it, number) }
            if (number != null) {
              "Will now automatically stop recording `$channelName` when there are **$number** or less people."
            } else {
              "Will no longer automatically stop recording `$channelName`."
            }
          }
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Could not parse number argument: ${e.message}")
      } catch (e: IllegalArgumentException) {
        throw InvalidCommand(::usage, "Number must be positive: ${e.message}")
      }

    BotUtils.sendMessage(event.channel, message + warningMessage)
  }

  override fun usage(prefix: String): String = "${prefix}autostop [Voice Channel name | 'all'] [number | 'off']"

  override fun description(): String = "Sets the number of players for the bot to autostop a voice channel. All will apply number to all voice channels."
}
