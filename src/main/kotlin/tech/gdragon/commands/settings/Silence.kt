package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Silence : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isNotEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val audioManager = event.guild.audioManager

    val isRecording = audioManager.connectedChannel
      ?.members
      ?.map { it.idLong }
      ?.containsAll(listOf(event.author.idLong, event.jda.selfUser.idLong)) ?: false

    if (isRecording) {
      val handler = audioManager.receivingHandler as? CombinedAudioRecorderHandler
      val ignoredUsers = _message
        ?.mentionedUsers
        ?.mapNotNull {
          val isSuccess = handler?.silenceUser(it) ?: false
          if (isSuccess) it else null
        }
        ?.joinToString { it.asMention }
      BotUtils.sendMessage(event.channel, ":hear_no_evil: _Ignoring the following users: ${ignoredUsers}_")
    } else {
      BotUtils.sendMessage(event.channel, ":no_entry_sign: _Didn't ignore any users._")
    }
  }

  override fun usage(prefix: String): String = "${prefix}silence bot#0000"

  override fun description(): String = "Ignores audio from a Bot during a recording session."

}
