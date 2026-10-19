package me.synergy.commands;

import java.util.List;

import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.modules.Locales;

public class DiscordCommand extends Command {

    public DiscordCommand() {
        super("discord link [user]", "discord confirm", "discord unlink");
    }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return Discord.getUsersTags();
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.discord.link", "link your Discord");
        Locales.addDefault("command.discord.confirm", "confirm linking");
        Locales.addDefault("command.discord.unlink", "unlink your Discord");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        switch (path) {
            case "discord.link" -> Synergy.event("discord-link").toProxy().setPlayerUniqueId(sender.uuid()).setOption("tag", args.length == 0 ? null : args[0]).send();
            case "discord.link.confirm" -> Synergy.event("discord-confirm").toProxy().setPlayerUniqueId(sender.uuid()).send();
            case "discord.unlink" -> Synergy.event("discord-unlink").toProxy().setPlayerUniqueId(sender.uuid()).send();
        }
    }
}
