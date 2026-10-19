package me.synergy.handlers;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.discord.RolesHandler;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.Messaging;
import me.synergy.modules.Tasks;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.twitch.Twitch;
import me.synergy.web.MonobankHandler;
import me.synergy.web.TikTokHandler;

public final class PlayerSessions {

    private PlayerSessions() {}

    private static final ExecutorService SESSIONS = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Synergy-Sessions").factory());
    private static final Set<UUID> kicked = ConcurrentHashMap.newKeySet();

    public static void joinAsync(UUID uuid, String name, String address) {
        SESSIONS.execute(() -> {
            try {
                if (join(uuid, name, address)) kicked.add(uuid);
            } catch (Throwable e) {
                Synergy.getLogger().error("Join of " + name + " failed: " + e, e);
            }
        });
    }

    public static boolean quitAsync(UUID uuid, String name) {
        boolean wasKicked = kicked.contains(uuid);
        SESSIONS.execute(() -> {
            try {
                quit(uuid, name, kicked.remove(uuid));
            } catch (Throwable e) {
                Synergy.getLogger().error("Quit of " + name + " failed: " + e, e);
            }
        });
        return wasKicked;
    }

    private static boolean join(UUID uuid, String name, String address) {
        UUID old = DataManager.findUuidByName(name);
        if (old != null && !old.equals(uuid)) {
            DataManager.migrateUuid(old, uuid);
            Synergy.getLogger().info("Migrated " + name + ": " + old + " -> " + uuid);
        }
        BreadMaker bread = Synergy.getBread(uuid);
        bread.getCache().clear();
        bread.setData("name", name);
        if (!Synergy.isProxy()) {
            Tasks.later(() -> Messaging.flush(null), Duration.ofSeconds(1));
            Synergy.getLogger().discord("```Player " + name + " has joined with IP " + address + " ```");
        }
        streams(bread, true);
        String kick = kickReason(bread);
        if (kick != null) {
            bread.kick(Config.getString(kick));
            return true;
        }
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
        if (Config.getBoolean("tiktok.enabled")) TikTokHandler.disconnect(uuid);
        if (kicked) return;
        if (!Synergy.isProxy()) Synergy.getLogger().discord("```Player " + name + " has left ```");
        streams(bread, false);
        announce(bread, name, "player.server.quit", "#fab1a0");
    }

    private static void streams(BreadMaker bread, boolean join) {
        String twitch = bread.getData("twitch-username").getAsString(), monobank = bread.getData("monobank").getAsString(), tiktok = bread.getData("tiktok").getAsString();
        try {
            if (Config.getBoolean("twitch.enabled") && twitch != null) {
                if (join) Twitch.getConnectionManager().connect(twitch, bread.getData("twitch-token").getAsString());
                else Twitch.getConnectionManager().disconnect(twitch);
            }
            if (Config.getBoolean("monobank.enabled") && monobank != null) {
                if (join) MonobankHandler.connect(bread.getName(), monobank);
                else MonobankHandler.disconnect(monobank);
            }
            if (join && Config.getBoolean("tiktok.enabled") && tiktok != null) TikTokHandler.connect(bread.getUniqueId(), tiktok);
        } catch (Exception e) {
            Synergy.getLogger().warning("Stream connection of " + bread.getName() + " failed: " + e.getMessage());
        }
    }

    private static String kickReason(BreadMaker bread) {
        boolean linked = bread.getData("discord").isSet();
        if (Config.getBoolean("discord.kick-player.if-has-no-link.enabled") && !linked) return "discord.kick-player.if-has-no-link.message";
        if (Synergy.getDiscord() == null || !linked) return null;
        if (Config.getBoolean("discord.kick-player.if-banned.enabled") && Discord.isBanned(bread)) return "discord.kick-player.if-banned.message";
        if (Config.getBoolean("discord.kick-player.if-missing.enabled") && Discord.isMissing(bread)) return "discord.kick-player.if-missing.message";
        if (Config.getBoolean("discord.kick-player.if-muted.enabled") && Discord.isMuted(bread)) return "discord.kick-player.if-muted.message";
        return null;
    }

    private static void announce(BreadMaker bread, String name, String key, String color) {
        if (Synergy.getDiscord() == null || !Config.getBoolean("discord.player-join-leave-messages")) return;
        SynergyEvent event = Synergy.event("discord-embed").toProxy().setPlayerUniqueId(bread.getUniqueId()).setOption("color", color)
            .setOption("author", LocaleBuilder.of(key).placeholder("player", name).placeholder("gender", bread.getGender().name()).stripped());
        if (Config.isFilled("chat-manager.chats.global.discord.channel")) event.setOption("channel", Config.getString("chat-manager.chats.global.discord.channel"));
        event.send();
    }
}
