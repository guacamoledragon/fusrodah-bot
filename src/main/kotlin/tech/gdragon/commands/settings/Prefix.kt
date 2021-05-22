package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Lang

class Prefix : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val newPrefix = args.first()
    val prefix = BotUtils.setPrefix(event.guild, newPrefix)

    val message =
      if (prefix == newPrefix) ":twisted_rightwards_arrows: _Command prefix now set to **`${prefix}`**._"
      else ":no_entry_sign: _Could not set to prefix **`$newPrefix`**._"

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}prefix [character]"

  override fun description(lang: Lang): String = "Sets the prefix for each command to avoid conflict with other bots (Default is '!')"
}
