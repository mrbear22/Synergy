package me.synergy.discord;

import java.awt.Color;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.Locales;
import me.synergy.modules.Punishments;
import me.synergy.modules.Tasks;
import me.synergy.objects.LocaleBuilder;
import me.synergy.objects.Punishment;
import me.synergy.objects.Punishment.Type;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Keeps Discord and the punishments table one system: a game/web punishment becomes a timeout or a ban on the guild,
 * and a timeout or ban a moderator gives on Discord becomes a punishment. Our own bot's audit entries are skipped,
 * so nothing loops.
 */
public class PunishmentSync extends ListenerAdapter implements SynergyListener {

    /** Discord's timeout limit; longer mutes are topped up by {@link #refresh()}. */
    private static final Duration MAX_TIMEOUT = Duration.ofDays(28);
    private static ScheduledFuture<?> refresher;

    public PunishmentSync() {
        Locales.addDefault("discord.ban.reason", "Discord ban");
        Locales.addDefault("punishment.discord.warn", "You have been warned on %server%");
        Synergy.getEventManager().registerEvents(this);
        if (refresher != null) refresher.cancel(false);
        refresher = Tasks.every(PunishmentSync::refresh, Duration.ofHours(1));
    }

    private static boolean enabled() {
        return Punishments.enabled() && Config.getBoolean("punishments.discord-sync") && Discord.getJda() != null && Discord.getGuild() != null;
    }

    private static UserSnowflake member(UUID uuid) {
        String id = DataManager.getData(uuid, "discord").getAsString();
        return id == null || id.isBlank() ? null : UserSnowflake.fromId(id);
    }

    private static String audit(Punishment p) {
        String reason = "#" + p.id() + " " + (p.staff() == null ? "" : p.staff() + ": ") + (p.reason() == null ? "" : p.reason());
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    /* ── Synergy → Discord ───────────────────────────────────── */

    @SynergyHandler("punishment")
    public void onPunishment(SynergyEvent event) {
        if (!enabled() || "discord".equals(event.getOption("origin").getAsString())) return;
        Punishment p = Punishment.fromJson(event.getOption("punishment").getAs(JsonObject.class));
        UserSnowflake user = member(p.uuid());
        if (user == null || p.type() == null) return;
        Guild guild = Discord.getGuild();
        boolean on = "issue".equals(event.getOption("action").getAsString());
        if (!on) Punishments.refresh(p.uuid());
        switch (p.type()) {
            case MUTE -> {
                Punishment mute = on ? p : Punishments.active(p.uuid(), Type.MUTE);
                if (mute != null) guild.timeoutFor(user, length(mute)).reason(audit(mute)).queue(null, e -> fail("timeout", e));
                else guild.removeTimeout(user).reason(audit(p)).queue(null, e -> fail("timeout removal", e));
            }
            case BAN -> {
                if (on) guild.ban(user, 0, TimeUnit.SECONDS).reason(audit(p)).queue(null, e -> fail("ban", e));
                else if (Punishments.active(p.uuid(), Type.BAN) == null) guild.unban(user).reason(audit(p)).queue(null, e -> fail("unban", e));
            }
            case WARN -> {
                if (!on) return;
                String language = Synergy.getBread(p.uuid()).getLanguage();
                EmbedBuilder embed = new EmbedBuilder().setColor(Color.decode("#f39c12"))
                    .setTitle(LocaleBuilder.of("punishment.discord.warn").placeholder("server", guild.getName()).stripped(language))
                    .setDescription(p.reason()).setFooter("#" + p.id() + " · " + p.staff());
                Synergy.getDiscord().retrieveUserById(user.getId()).flatMap(User::openPrivateChannel).flatMap(c -> c.sendMessageEmbeds(embed.build())).queue(null, e -> {});
            }
        }
    }

    private static Duration length(Punishment mute) {
        if (mute.permanent()) return MAX_TIMEOUT;
        Duration left = Duration.ofMillis(Math.max(1000, mute.expires() - System.currentTimeMillis()));
        return left.compareTo(MAX_TIMEOUT) > 0 ? MAX_TIMEOUT : left;
    }

    /** Tops up the timeout of mutes longer than Discord allows. */
    private static void refresh() {
        if (!enabled()) return;
        long soon = System.currentTimeMillis() + Duration.ofDays(2).toMillis();
        for (Punishment mute : DataManager.select("SELECT " + Punishment.COLUMNS + " FROM punishments WHERE type = 'mute' AND revoked IS NULL AND (expires IS NULL OR expires > ?)", Punishment::of, soon)) {
            UserSnowflake user = member(mute.uuid());
            Member member = user == null ? null : Discord.getGuild().getMemberById(user.getId());
            OffsetDateTime end = member == null ? null : member.getTimeOutEnd();
            if (member != null && (end == null || end.toInstant().toEpochMilli() < soon)) Discord.getGuild().timeoutFor(member, length(mute)).reason(audit(mute)).queue(null, e -> fail("timeout", e));
        }
    }

    private static void fail(String what, Throwable error) {
        Synergy.getLogger().warning("Discord " + what + " failed: " + error.getMessage());
    }

    /* ── Discord → Synergy ───────────────────────────────────── */

    @Override
    public void onGuildAuditLogEntryCreate(@NotNull GuildAuditLogEntryCreateEvent event) {
        AuditLogEntry entry = event.getEntry();
        if (!enabled() || !event.getGuild().getId().equals(Discord.getGuild().getId()) || entry.getUserIdLong() == event.getJDA().getSelfUser().getIdLong()) return;
        UUID uuid = DataManager.findUserUUID("discord", entry.getTargetId());
        if (uuid == null) return;
        User actor = event.getJDA().getUserById(entry.getUserIdLong());
        String staff = actor != null ? "@" + actor.getEffectiveName() : "Discord";
        UUID staffUuid = DataManager.findUserUUID("discord", entry.getUserId());
        String reason = entry.getReason() == null || entry.getReason().isBlank() ? null : entry.getReason();
        switch (entry.getType()) {
            case BAN -> Punishments.issue(uuid, Type.BAN, -1, reason != null ? reason : LocaleBuilder.of("discord.ban.reason").stripped(), staff, string(staffUuid), "discord");
            case UNBAN -> Punishments.revoke(uuid, Type.BAN, staff, reason, "discord");
            case MEMBER_UPDATE -> {
                AuditLogChange change = entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT);
                if (change == null) return;
                String until = change.getNewValue();
                long millis = until == null ? 0 : OffsetDateTime.parse(until).toInstant().toEpochMilli() - System.currentTimeMillis();
                if (millis <= 0) Punishments.revoke(uuid, Type.MUTE, staff, reason, "discord");
                else Punishments.issue(uuid, Type.MUTE, millis, reason != null ? reason : LocaleBuilder.of("discord.timeout.reason").stripped(), staff, string(staffUuid), "discord");
            }
            default -> {}
        }
    }

    private static String string(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
