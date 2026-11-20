package me.synergy.commands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import me.synergy.modules.DataManager;
import me.synergy.modules.Locales;
import me.synergy.modules.Punishments;
import me.synergy.objects.LocaleBuilder;
import me.synergy.objects.Punishment;
import me.synergy.objects.Punishment.Type;

public class PunishCommand extends Command {

    public PunishCommand() {
        super("warn <player> <reason...>", "mute|tempmute <player> <duration> <reason...>", "ban|tempban <player> <duration> <reason...>",
            "unwarn <player> [reason...]", "unmute <player> [reason...]", "unban <player> [reason...]", "punishments <player>");
    }

    @Override public String toggle() { return "punishments.enabled"; }

    @Override
    protected boolean console(String path) {
        return true;
    }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return name.equals("duration") ? List.of("10m", "30m", "1h", "6h", "1d", "7d", "30d", "perm") : super.values(sender, name, input);
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.warn", "warn a player (several: name1,name2)");
        Locales.addDefault("command.mute", "mute a player (10m, 2h, 1d, perm)");
        Locales.addDefault("command.ban", "ban a player (10m, 2h, 1d, perm)");
        Locales.addDefault("command.unwarn", "remove a player's active warnings");
        Locales.addDefault("command.unmute", "unmute a player");
        Locales.addDefault("command.unban", "unban a player");
        Locales.addDefault("command.punishments", "punishment history of a player");
        Locales.addDefault("punishment.done", "<success>Done: %count% (#%ids%).");
        Locales.addDefault("punishment.none", "<danger>%target% has no active %type%.");
        Locales.addDefault("punishment.duration.invalid", "<danger>Invalid duration. Use 10m, 2h, 1d, 1w or perm.");
        Locales.addDefault("punishment.history.header", "<primary>Punishments of <secondary>%target%<primary>:");
        Locales.addDefault("punishment.history.line", "<secondary>#%id% <primary>%date% <secondary>%type% <primary>%duration% · %staff%: %reason% %state%");
        Locales.addDefault("punishment.history.empty", "<primary>No punishments.");
        Locales.addDefault("punishment.state.active", "<danger>●");
        Locales.addDefault("punishment.state.over", "<success>○");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        List<UUID> targets = new ArrayList<>();
        for (String name : args[0].split(",")) {
            UUID uuid = DataManager.findUuidByName(name.trim());
            if (uuid == null) {
                sender.send(LocaleBuilder.of("player.name.unknown").placeholder("name", name.trim()));
                return;
            }
            targets.add(uuid);
        }
        String staff = sender.name(), staffUuid = sender.isPlayer() ? sender.uuid().toString() : null, source = sender.isPlayer() ? "game" : "console";
        if (path.equals("punishments")) {
            history(sender, args[0], targets.get(0));
            return;
        }
        List<Long> ids = new ArrayList<>();
        if (path.startsWith("un")) {
            Type type = Type.of(path.substring(2));
            String reason = args.length > 1 ? args[1] : null;
            for (UUID uuid : targets) Punishments.revoke(uuid, type, staff, reason, source).forEach(p -> ids.add(p.id()));
            if (ids.isEmpty()) {
                sender.send(LocaleBuilder.of("punishment.none").placeholder("target", args[0]).placeholder("type", "<locale:punishment.type." + type.id() + ">"));
                return;
            }
        } else {
            Type type = Type.of(path);
            Long millis = type == Type.WARN ? Long.valueOf(0) : Punishments.parseDuration(args[1]);
            if (millis == null) {
                sender.send("<locale:punishment.duration.invalid>");
                return;
            }
            String reason = args[type == Type.WARN ? 1 : 2];
            for (UUID uuid : targets) {
                Punishment p = Punishments.issue(uuid, type, millis, reason, staff, staffUuid, source);
                if (p != null) ids.add(p.id());
            }
        }
        sender.send(LocaleBuilder.of("punishment.done").placeholder("count", ids.size()).placeholder("ids", String.join(", #", ids.stream().map(String::valueOf).toList())));
    }

    private static void history(Sender sender, String name, UUID uuid) {
        List<Punishment> history = Punishments.history(uuid, 15);
        if (history.isEmpty()) {
            sender.send("<locale:punishment.history.empty>");
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yy HH:mm");
        List<String> lines = new ArrayList<>(List.of(LocaleBuilder.of("punishment.history.header").placeholder("target", name).build()));
        for (Punishment p : history) lines.add(LocaleBuilder.of("punishment.history.line").placeholder("id", p.id()).placeholder("date", format.format(new Date(p.created())))
            .placeholder("type", "<locale:punishment.type." + p.type().id() + ">").placeholder("duration", p.type() == Type.WARN ? "" : Punishments.duration(p))
            .placeholder("staff", p.staff()).placeholder("reason", p.reason()).placeholder("state", p.active() ? "<locale:punishment.state.active>" : "<locale:punishment.state.over>").build());
        sender.send(String.join("\n", lines));
    }
}
