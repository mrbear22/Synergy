package me.synergy.modules;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import com.google.gson.JsonObject;

import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.ChatHandler;
import me.synergy.integrations.LuckPermsAPI;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.objects.Punishment;
import me.synergy.objects.Punishment.Type;

/**
 * Warnings, mutes and bans. The punishments table is both the log and the source of active punishments; every change
 * goes out as a "punishment" event (action issue / revoke / expire) that each server, the Discord sync and other
 * plugins react to. Anything can punish through the "punish" and "punish-revoke" events, which is how the website does it.
 */
public final class Punishments implements Module {

    private record Active(List<Punishment> list, long loaded) {}

    private static final String SELECT = "SELECT " + Punishment.COLUMNS + " FROM punishments ";
    private static final String VOICE = "voicechat.speak";
    private static final long CACHE_MILLIS = 300_000;
    private static final Map<UUID, Active> cache = new ConcurrentHashMap<>();
    private static volatile boolean enabled;
    private static volatile long swept;
    private static ScheduledFuture<?> sweeper;

    @Override public String toggle() { return "punishments.enabled"; }

    @Override
    public void defaults() {
        Config.addDefault("punishments.enabled", true);
        Config.addDefault("punishments.broadcast", true);
        Config.addDefault("punishments.discord-sync", true);
        Config.addDefault("punishments.discord-channel", "00000000000000000");
        Config.addDefault("punishments.warnings.expire-days", 30);
        Config.addDefault("punishments.warnings.escalation", new String[] {"3 mute 1d", "5 ban 7d"});
        Locales.addDefault("punishment.type.warn", "warning");
        Locales.addDefault("punishment.type.mute", "mute");
        Locales.addDefault("punishment.type.ban", "ban");
        Locales.addDefault("punishment.permanent", "forever");
        Locales.addDefault("punishment.warn.target", "<danger>You have been warned: %reason%. Active warnings: %count%");
        Locales.addDefault("punishment.mute.target", "<danger>You have been muted for %duration%. Reason: %reason%");
        Locales.addDefault("punishment.mute.ended", "<success>You have been unmuted.");
        Locales.addDefault("punishment.ban.screen", "<danger>You are banned from the server\n\n<primary>Reason: <secondary>%reason%\n<primary>Expires: <secondary>%duration%\n<primary>Case: <secondary>#%id%");
        Locales.addDefault("punishment.broadcast.warn", "<danger>%staff% warned %target%. Reason: %reason%");
        Locales.addDefault("punishment.broadcast.mute", "<danger>%staff% muted %target% for %duration%. Reason: %reason%");
        Locales.addDefault("punishment.broadcast.ban", "<danger>%staff% banned %target% for %duration%. Reason: %reason%");
        Locales.addDefault("punishment.broadcast.unwarn", "<success>%staff% removed a warning of %target%.");
        Locales.addDefault("punishment.broadcast.unmute", "<success>%staff% unmuted %target%.");
        Locales.addDefault("punishment.broadcast.unban", "<success>%staff% unbanned %target%.");
        Locales.addDefault("punishment.escalation.reason", "%count% active warnings");
    }

    @Override
    public void start() {
        boolean sqlite = DataManager.sqlite();
        DataManager.update("CREATE TABLE IF NOT EXISTS punishments ("
            + (sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY")
            + ", uuid VARCHAR(36) NOT NULL, type VARCHAR(8) NOT NULL, reason TEXT, staff VARCHAR(64), staff_uuid VARCHAR(36), source VARCHAR(16) NOT NULL"
            + ", created BIGINT NOT NULL, expires BIGINT, revoked BIGINT, revoked_by VARCHAR(64), revoke_reason TEXT)"
            + (sqlite ? "" : " DEFAULT CHARSET=utf8mb4"));
        for (String index : new String[] {"punishments_player ON punishments (uuid, revoked)", "punishments_expires ON punishments (expires)", "punishments_created ON punishments (created)"})
            DataManager.tolerate("CREATE INDEX " + (sqlite ? "IF NOT EXISTS " : "") + index);
        enabled = true;
        Synergy.getEventManager().registerEvents(Punishments.class);
        if (!Synergy.isHub()) return;
        migrate();
        swept = System.currentTimeMillis() - Duration.ofDays(1).toMillis();
        sweeper = Tasks.every(Punishments::sweep, Duration.ofSeconds(15));
    }

    @Override
    public void stop() {
        enabled = false;
        if (sweeper != null) sweeper.cancel(false);
        cache.clear();
    }

    public static boolean enabled() {
        return enabled;
    }

    /* ── reading ─────────────────────────────────────────────── */

    public static List<Punishment> active(UUID uuid) {
        if (!enabled || uuid == null) return List.of();
        Active active = cache.get(uuid);
        if (active == null || System.currentTimeMillis() - active.loaded() > CACHE_MILLIS) active = load(uuid);
        return active.list().stream().filter(Punishment::active).toList();
    }

    /** The strongest active punishment of a type (permanent first, then the latest expiry), or null. */
    public static Punishment active(UUID uuid, Type type) {
        return active(uuid).stream().filter(p -> p.type() == type).max(Comparator.comparingLong(p -> p.expires() == null ? Long.MAX_VALUE : p.expires())).orElse(null);
    }

    public static long warnings(UUID uuid) {
        return active(uuid).stream().filter(p -> p.type() == Type.WARN).count();
    }

    /** Drops the cached state and reads it again from the database: on join, where a stale answer would let a banned player in. */
    public static void refresh(UUID uuid) {
        if (enabled && uuid != null) load(uuid);
    }

    private static Active load(UUID uuid) {
        long now = System.currentTimeMillis();
        Active active = new Active(DataManager.select(SELECT + "WHERE uuid = ? AND revoked IS NULL AND (expires IS NULL OR expires > ?)", Punishment::of, uuid.toString(), now), now);
        cache.put(uuid, active);
        return active;
    }

    public static Punishment get(long id) {
        return DataManager.select(SELECT + "WHERE id = ?", Punishment::of, id).stream().findFirst().orElse(null);
    }

    public static List<Punishment> history(UUID uuid, int limit) {
        return DataManager.select(SELECT + "WHERE uuid = ? ORDER BY id DESC LIMIT " + Math.max(1, limit), Punishment::of, uuid.toString());
    }

    /* ── changing ────────────────────────────────────────────── */

    /**
     * @param millis duration, -1 for permanent; for a warning 0 means the configured expiry
     * @param source where it came from: game, discord, web, console, auto
     */
    public static Punishment issue(UUID uuid, Type type, long millis, String reason, String staff, String staffUuid, String source) {
        if (!enabled || uuid == null || type == null) return null;
        long now = System.currentTimeMillis();
        int warnDays = Config.getInt("punishments.warnings.expire-days", 30);
        Long expires = millis > 0 ? now + millis : type == Type.WARN && warnDays > 0 ? now + Duration.ofDays(warnDays).toMillis() : null;
        long id = DataManager.insert("INSERT INTO punishments (uuid, type, reason, staff, staff_uuid, source, created, expires) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            uuid.toString(), type.id(), reason, staff, staffUuid, source, now, expires);
        if (id < 0) return null;
        Punishment punishment = new Punishment(id, uuid, type, reason, staff, staffUuid, source, now, expires, null, null, null);
        load(uuid);
        changed("issue", punishment, source);
        announce(punishment, true);
        if (type == Type.WARN) escalate(punishment);
        return punishment;
    }

    /** Revokes every active punishment of the type. */
    public static List<Punishment> revoke(UUID uuid, Type type, String staff, String reason, String source) {
        if (!enabled || uuid == null) return List.of();
        return load(uuid).list().stream().filter(p -> p.type() == type && p.active()).map(p -> revoke(p, staff, reason, source)).filter(Objects::nonNull).toList();
    }

    public static Punishment revoke(long id, String staff, String reason, String source) {
        Punishment punishment = enabled ? get(id) : null;
        return punishment == null || punishment.revoked() != null ? null : revoke(punishment, staff, reason, source);
    }

    private static Punishment revoke(Punishment p, String staff, String reason, String source) {
        long now = System.currentTimeMillis();
        if (DataManager.update("UPDATE punishments SET revoked = ?, revoked_by = ?, revoke_reason = ? WHERE id = ? AND revoked IS NULL", now, staff, reason, p.id()) != 1) return null;
        Punishment revoked = new Punishment(p.id(), p.uuid(), p.type(), p.reason(), p.staff(), p.staffUuid(), p.source(), p.created(), p.expires(), now, staff, reason);
        load(p.uuid());
        changed("revoke", revoked, source);
        if (p.active()) announce(revoked, false);
        return revoked;
    }

    /** Hub only: turns punishments whose time ran out into "expire" events, so Discord unbans and servers lift mutes without polling. */
    private static void sweep() {
        long now = System.currentTimeMillis(), from = swept;
        swept = now;
        for (Punishment p : DataManager.select(SELECT + "WHERE type <> 'warn' AND revoked IS NULL AND expires > ? AND expires <= ?", Punishment::of, from, now)) {
            if (p.type() == null) continue;
            load(p.uuid());
            changed("expire", p, "auto");
            if (p.type() == Type.MUTE && active(p.uuid(), Type.MUTE) == null) LocaleBuilder.of("punishment.mute.ended").send(p.uuid());
        }
    }

    private static void changed(String action, Punishment p, String origin) {
        SynergyEvent event = Synergy.event("punishment").setPlayerUniqueId(p.uuid()).setOption("action", action).setOption("origin", origin).setOption("punishment", p.toJson());
        event.send();
        // An untargeted event sent by the proxy only reaches the backends
        if (Synergy.isProxy() && Messaging.isEnabled()) event.fireEvent();
    }

    private static void announce(Punishment p, boolean issued) {
        String target = name(p.uuid()), staff = issued ? p.staff() : p.revokedBy();
        if (issued && p.type() != Type.BAN) LocaleBuilder.of("punishment." + p.type().id() + ".target").placeholder("reason", p.reason())
            .placeholder("duration", duration(p)).placeholder("count", warnings(p.uuid())).send(p.uuid());
        if (!issued && p.type() == Type.MUTE && active(p.uuid(), Type.MUTE) == null) LocaleBuilder.of("punishment.mute.ended").send(p.uuid());
        LocaleBuilder message = LocaleBuilder.of("punishment.broadcast." + (issued ? "" : "un") + p.type().id()).placeholder("staff", staff == null ? "Synergy" : staff)
            .placeholder("target", target).placeholder("duration", duration(p)).placeholder("reason", p.reason() == null ? "-" : p.reason());
        if (Config.getBoolean("punishments.broadcast")) Synergy.event("broadcast").setOption("message", message.build()).send();
        SynergyEvent embed = Synergy.event("discord-embed").toProxy().setPlayerUniqueId(p.uuid()).setOption("author", message.stripped()).setOption("player", target)
            .setOption("footer", "#" + p.id() + " · " + p.source()).setOption("timestamp", "true")
            .setOption("color", !issued ? "#2ecc71" : switch (p.type()) { case WARN -> "#f39c12"; case MUTE -> "#e67e22"; case BAN -> "#e74c3c"; });
        if (Config.isFilled("punishments.discord-channel")) embed.setOption("channel", Config.getString("punishments.discord-channel"));
        embed.send();
    }

    private static void escalate(Punishment warn) {
        long count = warnings(warn.uuid());
        for (String rule : Config.getStringList("punishments.warnings.escalation")) {
            String[] parts = rule.trim().split("\\s+");
            if (parts.length < 3 || !parts[0].equals(String.valueOf(count))) continue;
            Type type = Type.of(parts[1]);
            Long millis = parseDuration(parts[2]);
            if (type == null || type == Type.WARN || millis == null) continue;
            issue(warn.uuid(), type, millis, LocaleBuilder.of("punishment.escalation.reason").placeholder("count", count).stripped(), "Synergy", null, "auto");
        }
    }

    /* ── effects on every server ─────────────────────────────── */

    @SynergyHandler("punishment")
    public static void onPunishment(SynergyEvent event) {
        cache.remove(event.getPlayerUniqueId());
        Punishment p = Punishment.fromJson(event.getOption("punishment").getAs(JsonObject.class));
        String action = event.getOption("action").getAsString();
        boolean on = "issue".equals(action);
        Synergy.platform().onPunishment(p, action, event.getOption("origin").getAsString());
        if (p.type() == Type.BAN && on && Synergy.platform().isOnline(p.uuid())) {
            kick(p.uuid(), screen(p));
        } else if (p.type() == Type.MUTE) {
            Punishment mute = on ? p : active(p.uuid(), Type.MUTE);
            Synergy.platform().onMuteChange(p.uuid(), mute != null, mute == null ? 0 : mute.expires() == null ? -1 : mute.expires(),
                mute == null ? null : mute.reason(), mute == null ? null : mute.staff());
            if (!Synergy.isHub() || !LuckPermsAPI.enabled()) return;
            if (mute != null) LuckPermsAPI.deny(p.uuid(), VOICE, mute.expires() == null ? null : Duration.ofMillis(Math.max(1000, mute.expires() - System.currentTimeMillis())));
            else LuckPermsAPI.allow(p.uuid(), VOICE);
        }
    }

    /* ── the event API: what the website and other plugins call ── */

    /** Options: players (uuids or names) or the event's player, type, duration ("1d", "perm"; optional for warn), reason, staff, staff-uuid, source. Replies with ids. */
    @SynergyHandler("punish")
    public static void onPunish(SynergyEvent event) {
        Type type = Type.of(event.getOption("type").getAsString());
        String reason = event.getOption("reason").getAsString(), duration = event.getOption("duration").getAsString();
        Long millis = type == Type.WARN && (duration == null || duration.isBlank()) ? Long.valueOf(0) : parseDuration(duration);
        List<UUID> players = players(event);
        if (type == null || millis == null || reason == null || reason.isBlank() || players.isEmpty()) {
            event.reply().setOption("error", type == null ? "unknown type" : millis == null ? "invalid duration" : players.isEmpty() ? "no players" : "no reason").send();
            return;
        }
        String staff = or(event.getOption("staff").getAsString(), "Synergy"), source = or(event.getOption("source").getAsString(), "web");
        List<Long> ids = players.stream().map(uuid -> issue(uuid, type, millis, reason, staff, event.getOption("staff-uuid").getAsString(), source))
            .filter(Objects::nonNull).map(Punishment::id).toList();
        event.reply().setOption("ids", ids).setOption("failed", players.size() - ids.size()).send();
    }

    /** Options: ids, or players with type; staff, reason, source. Replies with the revoked ids. */
    @SynergyHandler("punish-revoke")
    public static void onRevoke(SynergyEvent event) {
        String staff = or(event.getOption("staff").getAsString(), "Synergy"), reason = event.getOption("reason").getAsString(), source = or(event.getOption("source").getAsString(), "web");
        List<Punishment> revoked = new ArrayList<>();
        long[] ids = event.getOption("ids").isSet() ? event.getOption("ids").getAs(long[].class) : new long[0];
        for (long id : ids) revoked.add(revoke(id, staff, reason, source));
        Type type = Type.of(event.getOption("type").getAsString());
        if (type != null) players(event).forEach(uuid -> revoked.addAll(revoke(uuid, type, staff, reason, source)));
        event.reply().setOption("ids", revoked.stream().filter(Objects::nonNull).map(Punishment::id).toList()).send();
    }

    private static List<UUID> players(SynergyEvent event) {
        List<UUID> players = new ArrayList<>();
        if (event.getPlayerUniqueId() != null) players.add(event.getPlayerUniqueId());
        String[] listed = event.getOption("players").isSet() ? event.getOption("players").getAs(String[].class) : new String[0];
        for (String player : listed) {
            UUID uuid = player.length() == 36 ? UUID.fromString(player) : DataManager.findUuidByName(player);
            if (uuid != null && !players.contains(uuid)) players.add(uuid);
        }
        return players;
    }

    /* ── helpers ─────────────────────────────────────────────── */

    /** "30s", "10m", "2h", "1d", "1w", "1y", or "perm" (-1). Null when it can't be parsed. */
    public static Long parseDuration(String input) {
        if (input == null) return null;
        input = input.trim().toLowerCase();
        if (input.matches("perm|permanent|forever|-1")) return -1L;
        if (!input.matches("\\d{1,6}[smhdwy]")) return null;
        long unit = switch (input.charAt(input.length() - 1)) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            case 'w' -> 604_800_000L;
            default -> 31_536_000_000L;
        };
        return Long.parseLong(input.substring(0, input.length() - 1)) * unit;
    }

    /** The full length of a punishment, for messages. */
    public static String duration(Punishment p) {
        return p.permanent() ? "<locale:punishment.permanent>" : ChatHandler.formatDuration((p.expires() - p.created()) / 1000);
    }

    public static String screen(Punishment ban) {
        return LocaleBuilder.of("punishment.ban.screen").placeholder("reason", ban.reason() == null ? "-" : ban.reason()).placeholder("id", ban.id()).placeholder("staff", ban.staff())
            .placeholder("duration", ban.permanent() ? "<locale:punishment.permanent>" : ChatHandler.formatDuration(ban.remainingSeconds())).build();
    }

    public static void kick(UUID uuid, String reason) {
        BreadMaker bread = Synergy.getBread(uuid);
        Synergy.platform().kick(uuid, Synergy.translate(reason, bread.getLanguage()).setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
    }

    private static String name(UUID uuid) {
        return or(Synergy.getBread(uuid).getName(), uuid.toString());
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Moves mutes kept by older versions in the synergy table into the punishments table. */
    private static void migrate() {
        List<String[]> old = DataManager.select("SELECT uuid, value FROM synergy WHERE option = 'mute-expires'", r -> new String[] {r.getString(1), r.getString(2)});
        long now = System.currentTimeMillis();
        for (String[] row : old) {
            UUID uuid = UUID.fromString(row[0]);
            long expires = row[1] == null ? 0 : Long.parseLong(row[1]);
            if (expires == -1 || expires > now) DataManager.insert("INSERT INTO punishments (uuid, type, reason, staff, source, created, expires) VALUES (?, 'mute', ?, ?, 'game', ?, ?)",
                row[0], DataManager.getData(uuid, "mute-reason").getAsString(), DataManager.getData(uuid, "mute-by").getAsString(), now, expires == -1 ? null : expires);
            DataManager.update("DELETE FROM synergy WHERE uuid = ? AND option IN ('mute-expires', 'mute-reason', 'mute-by')", row[0]);
        }
        if (!old.isEmpty()) Synergy.getLogger().info("Moved " + old.size() + " mutes into the punishments table");
    }
}
