package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import net.dv8tion.jda.core.entities.Channel as DiscordChannel

class AutoStop : CommandHandler {
  private fun updateChannelAutoStop(channel: DiscordChannel, autoStop: Int) {
    transaction {
      val guild = channel.guild.run {
        Guild.findOrCreate(idLong, name, region.name)
      }

      Channel
        .findOrCreate(channel.idLong, channel.name, guild)
//        .forEach { it.autoStop = autoStop }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    /*require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int = args.last().toInt()

        check(number > 0) {
          "Number must be positive!"
        }

        if (channelName == "all") {
          val channels = event.guild.voiceChannels
          channels.forEach { updateChannelAutoStop(it, number) }
          "Will now automatically leave any voice channel with $number or less people."
        } else {
          val channels = event.guild.getVoiceChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            "Cannot find voice channel $channelName."
          } else {
            channels.forEach { updateChannelAutoStop(it, number) }
            "Will now automatically leave '$channelName' when there are $number or less people."
          }
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Could not parse number argument: ${e.message}")
      } catch (e: IllegalArgumentException) {
        throw InvalidCommand(::usage, "Number must be positive: ${e.message}")
      }*/

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _autostop is currently disabled due to some bugs_")
  }

  override fun usage(prefix: String): String = "${prefix}autostop [Voice Channel name | 'all'] [number]"

  override fun description(): String = "Sets the number of players for the bot to autostop a voice channel. All will apply number to all voice channels."
}
