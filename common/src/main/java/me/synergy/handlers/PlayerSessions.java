package me.synergy.handlers;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.discord.RolesHandler;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.modules.Messaging;
import me.synergy.modules.Module;
import me.synergy.modules.Punishments;
import me.synergy.objects.Punishment;
import me.synergy.modules.Tasks;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import net.kyori.adventure.text.Component;

public final class PlayerSessions implements Module {

    private static final List<String> REASONS = List.of("has-no-link", "banned", "missing", "muted");

    private static final ExecutorService SESSIONS = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Synergy-Sessions").factory());
    private static final Set<UUID> kicked = ConcurrentHashMap.newKeySet();

    @Override
    public void defaults() {
        Locales.addDefault("discord.kick.has-no-link", "Link your Discord to play on this server: /discord link");
        Locales.addDefault("discord.kick.banned", "You are banned on our Discord server.");
        Locales.addDefault("discord.kick.missing", "You are not on our Discord server.");
        Locales.addDefault("discord.kick.muted", "You have an active timeout on our Discord server.");
        for (String reason : REASONS) {
            Config.addDefault("discord.kick-player.if-" + reason + ".enabled", false);
            Config.addDefault("discord.kick-player.if-" + reason + ".message", "<locale:discord.kick." + reason + ">");
        }
    }

    @Override
    public void start() {
        Synergy.getEventManager().registerEvents(PlayerSessions.class);
    }

    @SynergyHandler("discord-kick")
    public static void onDiscordKick(SynergyEvent event) {
        kick(event.getBread(), List.of(event.getOption("reason").getAsString()));
    }

    /** Kicks a player wherever they are: target the event at "@uuid" or send it to every server. Option: reason (MiniMessage/locale). */
    @SynergyHandler("kick")
    public static void onKick(SynergyEvent event) {
        BreadMaker bread = event.getBread();
        if (!bread.isOnline()) return;
        bread.kick(event.getOption("reason").isSet() ? event.getOption("reason").getAsString() : "");
        event.reply().setOption("ok", true).send();
    }

    /** Who is online and where, as the hub sees it: { servers: { name: [players] } }. */
    @SynergyHandler("online")
    public static void onOnline(SynergyEvent event) {
        event.reply().setOption("servers", Synergy.platform().playersByServer()).send();
    }

    /** Called on the hub when the player's Discord membership changed: kicks them if their current state is punishable. */
    public static void discord(UUID uuid) {
        if (uuid == null) return;
        Tasks.async(() -> {
            BreadMaker bread = Synergy.getBread(uuid);
            String reason = reason(reasons(bread));
            if (reason == null) return;
            kick(bread, List.of(reason));
            Synergy.event("discord-kick").toPlayer(uuid).setOption("reason", reason).send();
        });
    }

    private static String reason(Collection<String> reasons) {
        return REASONS.stream().filter(reasons::contains).filter(r -> Config.getBoolean("discord.kick-player.if-" + r + ".enabled")).findFirst().orElse(null);
    }

    private static boolean kick(BreadMaker bread, Collection<String> reasons) {
        String reason = reason(reasons);
        if (reason != null) bread.kick(Config.getString("discord.kick-player.if-" + reason + ".message"));
        return reason != null;
    }

    private static List<String> reasons(BreadMaker bread) {
        if (!bread.getData("discord").isSet()) return List.of("has-no-link");
        if (REASONS.stream().skip(1).noneMatch(r -> Config.getBoolean("discord.kick-player.if-" + r + ".enabled"))) return List.of();
        if (Synergy.getDiscord() != null) return Discord.reasons(bread);
        try {
            String reasons = Synergy.event("discord-reasons").toProxy().setPlayerUniqueId(bread.getUniqueId()).request(Duration.ofSeconds(3)).join().getOption("reasons").getAsString();
            return reasons == null || reasons.isEmpty() ? List.of() : List.of(reasons.split(","));
        } catch (Exception e) {
            Synergy.getLogger().warning("Discord check of " + bread.getName() + " failed: " + e.getMessage());
            return List.of();
        }
    }

    /** Warms the player's data before the session starts, for platforms that have a pre-login hook. */
    public static void preload(UUID uuid) {
        BreadMaker bread = Synergy.getBread(uuid);
        for (String option : new String[] {"twitch-username", "twitch-token", "monobank", "discord", "confirm-discord", "last-voted", "gender", "chat", "theme", "name", "votes", "language"})
            bread.getData(option);
    }

    /** Logs a kick and returns its reason translated for the player, or null when there is no reason to show. */
    public static Component kicked(UUID uuid, String name, Component reason) {
        BreadMaker bread = Synergy.getBread(uuid);
        Synergy.getLogger().discord("```Player " + name + " has been kicked with the reason: "
            + (reason == null ? "no reason" : Synergy.translate(reason, bread.getLanguage()).getStripped()) + "```");
        return reason == null ? null : Synergy.translate(reason, bread.getLanguage()).getColoredComponent(bread.getTheme());
    }

    public static void joinAsync(UUID uuid, String name, String address) {
        SESSIONS.execute(() -> {
            try {
                if (join(uuid, name, address)) kicked.add(uuid);
            } catch (Throwable e) {
                Synergy.getLogger().error("Join of " + name + " failed: " + e, e);
            }
        });
    }

    public static void quitAsync(UUID uuid, String name) {
        SESSIONS.execute(() -> {
            try {
                quit(uuid, name, kicked.remove(uuid));
            } catch (Throwable e) {
                Synergy.getLogger().error("Quit of " + name + " failed: " + e, e);
            }
        });
    }

    private static boolean join(UUID uuid, String name, String address) {
        BreadMaker bread = Synergy.getBread(uuid);
        bread.getCache().clear();
        bread.setData("name", name);
        if (!Synergy.isProxy()) {
            Tasks.later(() -> Messaging.flush(null), Duration.ofSeconds(1));
            Synergy.getLogger().discord("```Player " + name + " has joined with IP " + address + " ```");
        }
        Punishments.refresh(uuid);
        Punishment ban = Punishments.active(uuid, Punishment.Type.BAN);
        if (ban != null) {
            bread.kick(Punishments.screen(ban));
            return true;
        }
        if (kick(bread, reasons(bread))) return true;
        modules(bread, true);
        announce(bread, name, "player.server.join", "#81ecec");
        RolesHandler.sync(uuid);
        String confirm = bread.getData("confirm-discord").getAsString();
        if (confirm != null && Synergy.getDiscord() != null)
            Tasks.later(() -> LocaleBuilder.of("discord.link.confirm").placeholder("account", Discord.name(confirm)).send(bread), Duration.ofSeconds(2));
        return false;
    }

    private static void quit(UUID uuid, String name, boolean kicked) {
        BreadMaker bread = Synergy.getBread(uuid);
        bread.getCache().clear();
        modules(bread, false);
        if (kicked) return;
        if (!Synergy.isProxy()) Synergy.getLogger().discord("```Player " + name + " has left ```");
        announce(bread, name, "player.server.quit", "#fab1a0");
    }

    private static void modules(BreadMaker bread, boolean join) {
        for (Module module : Synergy.modules()) {
            try {
                if (join) module.join(bread);
                else module.quit(bread);
            } catch (Exception e) {
                Synergy.getLogger().warning(module.name() + " session " + (join ? "start" : "end") + " of " + bread.getName() + " failed: " + e.getMessage());
            }
        }
    }

    /** Join/leave announcement for every platform: in chat on the backend the player is on (platforms only mute the vanilla line), and as a Discord embed where the bot runs. */
    private static void announce(BreadMaker bread, String name, String key, String color) {
        LocaleBuilder message = LocaleBuilder.of(key).placeholder("player", name).placeholder("gender", bread.getGender().name());
        if (!Synergy.isProxy()) Synergy.platform().online().forEach(uuid -> MessageHandler.send(uuid, message.build(), false, false));
        if (Synergy.getDiscord() == null || !Config.getBoolean("discord.player-join-leave-messages")) return;
        SynergyEvent event = Synergy.event("discord-embed").toProxy().setPlayerUniqueId(bread.getUniqueId()).setOption("color", color)
            .setOption("author", message.stripped());
        if (Config.isFilled("chat-manager.chats.global.discord.channel")) event.setOption("channel", Config.getString("chat-manager.chats.global.discord.channel"));
        event.send();
    }
}
