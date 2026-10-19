package me.synergy.commands;

import java.util.List;
import java.util.UUID;

import me.synergy.brains.Synergy;
import me.synergy.modules.DataManager;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;

public class TempMuteCommand extends Command {

    public TempMuteCommand() {
        super("tempmute <player> <duration> <reason...>", "unmute <player>");
    }

    @Override public String toggle() { return "chat-manager.enabled"; }

    @Override
    protected boolean console(String path) {
        return true;
    }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return name.equals("duration") ? List.of("10m", "30m", "1h", "6h", "1d", "7d", "perm") : super.values(sender, name, input);
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.tempmute", "mute a player (10m, 2h, 1d, perm)");
        Locales.addDefault("command.unmute", "unmute a player");
        Locales.addDefault("mute.on.staff", "<success>%target% is muted.");
        Locales.addDefault("mute.off.staff", "<success>%target% is unmuted.");
        Locales.addDefault("mute.on.target", "<danger>You have been muted. Reason: %reason%");
        Locales.addDefault("mute.off.target", "<success>You have been unmuted.");
        Locales.addDefault("mute.on.broadcast", "<danger>%staff% muted %target% for %duration%. Reason: %reason%");
        Locales.addDefault("mute.off.broadcast", "<success>%staff% unmuted %target%.");
        Locales.addDefault("mute.duration.invalid", "<danger>Invalid duration. Use 10m, 2h, 1d or perm.");
        Locales.addDefault("mute.duration.permanent", "forever");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        UUID uuid = DataManager.findUuidByName(args[0]);
        if (uuid == null) {
            sender.send(LocaleBuilder.of("player.name.unknown").placeholder("name", args[0]));
            return;
        }
        BreadMaker bread = Synergy.getBread(uuid);
        boolean mute = path.equals("tempmute");
        Long duration = mute ? duration(args[1]) : null;
        if (mute && duration == null) {
            sender.send("<locale:mute.duration.invalid>");
            return;
        }
        String reason = mute ? args[2] : null;
        if (mute) bread.mute(duration, reason, sender.name());
        else bread.unmute();
        sender.send(LocaleBuilder.of(mute ? "mute.on.staff" : "mute.off.staff").placeholder("target", args[0]));
        bread.sendMessage(mute ? LocaleBuilder.of("mute.on.target").placeholder("reason", reason).build() : "<locale:mute.off.target>");
        LocaleBuilder broadcast = LocaleBuilder.of(mute ? "mute.on.broadcast" : "mute.off.broadcast").placeholder("target", args[0]).placeholder("staff", sender.name())
            .placeholder("duration", mute ? duration == -1L ? "<locale:mute.duration.permanent>" : args[1] : null).placeholder("reason", reason);
        Synergy.event("broadcast").setOption("message", broadcast.build()).send();
        Synergy.event("discord-embed").toProxy().setOption("title", broadcast.stripped()).setOption("color", mute ? "#e74c3c" : "#2ecc71").send();
        Synergy.getLogger().discord("```[MOD] " + sender.name() + (mute ? " muted " + args[0] + " for " + args[1] + " (" + reason + ")" : " unmuted " + args[0]) + "```");
    }

    private static Long duration(String input) {
        if (input.equalsIgnoreCase("perm")) return -1L;
        if (!input.matches("\\d+[smhdw]")) return null;
        long unit = switch (input.charAt(input.length() - 1)) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 604_800_000L;
        };
        return Long.parseLong(input.substring(0, input.length() - 1)) * unit;
    }
}
