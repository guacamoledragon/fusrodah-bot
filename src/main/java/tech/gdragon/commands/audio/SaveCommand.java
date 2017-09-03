package tech.gdragon.commands.audio;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;


public class SaveCommand implements Command {

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length > 1) {
      String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordBot.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    if (e.getGuild().getAudioManager().getConnectedChannel() == null) {
      DiscordBot.sendMessage(e.getChannel(), "I wasn't recording!");
      return;
    }

    if (args.length == 0)
      DiscordBot.writeToFile(e.getGuild(), e.getChannel());

    else if (args.length == 1) {

      //cut off # in channel name if they included it
      if (args[0].startsWith("#")) {
        args[0] = args[0].substring(1);
      }

      if (e.getGuild().getTextChannelsByName(args[0], true).size() == 0) {
        DiscordBot.sendMessage(e.getChannel(), "Cannot find specified text channel");
        return;
      }
      DiscordBot.writeToFile(e.getGuild(), e.getGuild().getTextChannelsByName(args[0], true).get(0));
    }
  }

  @Override
  public String usage(String prefix) {
    return prefix + "save | " + prefix + "save [text channel output]";
  }

  @Override
  public String description() {
    return "Saves the current recording and outputs it to the current or specified text chats (caps at 16MB)";
  }
}
