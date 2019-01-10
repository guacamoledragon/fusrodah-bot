package tech.gdragon.commands.misc

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Leave : CommandHandler {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)

      val message =
        if (event.guild.audioManager.isConnected) {
          val voiceChannel = event.guild.audioManager.connectedChannel

          guild?.settings?.let {
            if (it.autoSave) {
              val audioReceiveHandler = event.guild.audioManager.receiveHandler as CombinedAudioRecorderHandler
              audioReceiveHandler.saveRecording(voiceChannel, event.channel)
            }
          }

          BotUtils.leaveVoiceChannel(voiceChannel)
          ":wave: _Leaving **<#${voiceChannel.id}>**_"
        } else {
          ":no_entry_sign: _I am not in a channel_"
        }

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}leave"

  override fun description(): String = "Force the bot to leave it's current channel"
}
