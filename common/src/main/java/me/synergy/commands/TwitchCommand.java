package me.synergy.commands;

import java.util.List;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;

public class TwitchCommand extends Command {

    public TwitchCommand() {
        super("twitch link <channel> <token>", "twitch unlink", "twitch createreward <reward>", "twitch removereward <reward>", "twitch testreward <reward> <input...>");
    }

    @Override public String toggle() { return "twitch.enabled"; }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return name.equals("reward") ? List.copyOf(Config.getConfigurationSection("twitch.rewards").keySet()) : List.of();
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.twitch.link", "link your channel (token: twitchtokengenerator.com)");
        Locales.addDefault("command.twitch.unlink", "unlink your channel");
        Locales.addDefault("command.twitch.createreward", "create a channel points reward");
        Locales.addDefault("command.twitch.removereward", "remove a channel points reward");
        Locales.addDefault("command.twitch.testreward", "test a reward");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        String channel = sender.bread().getData("twitch-username").getAsString();
        if (path.equals("twitch.link") ? channel != null : channel == null) {
            sender.send(channel != null ? "<locale:account.link.exists service=Twitch command=\"/twitch unlink\">" : "<locale:account.link.missing service=Twitch command=\"/twitch link\">");
            return;
        }
        String reward = path.endsWith("reward") ? "twitch.rewards." + args[0] : null;
        switch (path) {
            case "twitch.link" -> Synergy.event("make-twitch-link").setPlayerUniqueId(sender.uuid()).setOption("tag", args[0]).setOption("token", args[1]).send();
            case "twitch.unlink" -> Synergy.event("remove-twitch-link").setPlayerUniqueId(sender.uuid()).send();
            case "twitch.createreward" -> Synergy.event("create-twitch-reward").setPlayerUniqueId(sender.uuid())
                .setOption("title", Config.getString(reward + ".title"))
                .setOption("cost", String.valueOf(Config.getInt(reward + ".cost")))
                .setOption("description", Config.getString(reward + ".description"))
                .setOption("input-required", String.valueOf(Config.getBoolean(reward + ".input-required")))
                .send();
            case "twitch.removereward" -> Synergy.event("remove-twitch-reward").setPlayerUniqueId(sender.uuid()).setOption("title", Config.getString(reward + ".title")).send();
            case "twitch.testreward" -> Synergy.event("twitch-reward-redeemed").setPlayerUniqueId(sender.uuid())
                .setOption("reward-title", Config.getString(reward + ".title"))
                .setOption("viewer-name", "test")
                .setOption("channel-name", channel)
                .setOption("viewer-input", args[1])
                .setOption("reward-id", "-1")
                .send();
        }
    }
}
