package me.synergy.web;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.live.LiveClient;
import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.StreamRewards;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.modules.Module;
import me.synergy.modules.Tasks;
import me.synergy.objects.LocaleBuilder;
import me.synergy.objects.BreadMaker;

public class TikTokHandler implements Module {

    private static final Map<UUID, AtomicReference<LiveClient>> clients = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Viewer>> viewers = new ConcurrentHashMap<>();

    @Override public String toggle() { return "tiktok.enabled"; }

    @Override
    public void defaults() {
        Locales.addDefault("tiktok.live.connecting", "<secondary>Connecting to <primary>@%username%<secondary>...");
        Locales.addDefault("tiktok.live.connected", "<success>Connected to <primary>@%username%<success>!");
        Locales.addDefault("tiktok.live.disconnected", "<secondary>Disconnected from TikTok Live");
        Locales.addDefault("tiktok.live.error", "<danger>TikTok error: %message%");
        Config.addDefault("tiktok.enabled", false);
        if (Synergy.isProxy()) return;
        Config.addDefault("tiktok.follow-cooldown", 10000);
        Config.addDefault("tiktok.like-debounce-delay", 25000);
        if (!Config.getConfigurationSection("tiktok.rewards").isEmpty()) return;
        Config.addDefault("tiktok.rewards.small.title", "Small Gift");
        Config.addDefault("tiktok.rewards.small.gifts", new String[] {"Rose", "TikTok", "Heart", "GG"});
        Config.addDefault("tiktok.rewards.small.commands", new String[] {"say &e%viewer_name% &7sent &e%gift_count%x %gift_name% &7to &e%streamer_name%!"});
        Config.addDefault("tiktok.rewards.default.title", "Gift");
        Config.addDefault("tiktok.rewards.default.commands", new String[] {"say &e%viewer_name% &7sent &e%gift_count%x %gift_name%&7!"});
        Config.addDefault("tiktok.rewards.small-like.title", "Small Like");
        Config.addDefault("tiktok.rewards.small-like.threshold", 1);
        Config.addDefault("tiktok.rewards.small-like.commands", new String[] {"say &b%viewer_name% &7sent &b%like_count% likes!"});
        Config.addDefault("tiktok.rewards.follow.title", "Follow");
        Config.addDefault("tiktok.rewards.follow.commands", new String[] {"say &d%viewer_name% &7followed!"});
        Config.addDefault("tiktok.rewards.chat.title", "Chat");
        Config.addDefault("tiktok.rewards.chat.commands", new String[] {"say &7[TikTok] &f%viewer_name%&7: %message%"});
    }

    @Override
    public void start() {
        Synergy.getEventManager().registerEvents(TikTokHandler.class);
    }

    @Override
    public void stop() {
        clients.values().forEach(ref -> close(ref.get()));
        clients.clear();
    }

    @Override
    public void join(BreadMaker bread) {
        String username = bread.getData("tiktok").getAsString();
        if (Synergy.isHub() && username != null) connect(bread.getUniqueId(), username);
    }

    @Override
    public void quit(BreadMaker bread) {
        if (Synergy.isHub() && bread.getData("tiktok").isSet()) disconnect(bread.getUniqueId());
    }

    /** Asks the hub to connect (username) or disconnect (null) the player's TikTok Live. */
    public static void request(UUID uuid, String username) {
        SynergyEvent event = Synergy.event(username == null ? "tiktok-disconnect" : "tiktok-connect").toProxy().setPlayerUniqueId(uuid);
        if (username != null) event.setOption("username", username);
        event.send();
    }

    @SynergyHandler({"tiktok-connect", "tiktok-disconnect"})
    public static void onControl(SynergyEvent event) {
        if (!Synergy.isHub()) return;
        if (event.getIdentifier().equals("tiktok-connect")) connect(event.getPlayerUniqueId(), event.getOption("username").getAsString());
        else disconnect(event.getPlayerUniqueId());
    }

    @SynergyHandler({"tiktok-gift", "tiktok-like", "tiktok-follow", "tiktok-chat"})
    public static void onStream(SynergyEvent event) {
        UUID uuid = event.getPlayerUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        if (Synergy.isProxy() || !bread.isOnline()) return;
        String viewer = event.getOption("viewer-name").getAsString();
        switch (event.getIdentifier()) {
            case "tiktok-gift" -> {
                String gift = event.getOption("gift-name").getAsString();
                String count = event.getOption("combo-count").getAsString();
                Synergy.getLogger().info("[TikTok] [" + bread.getName() + "] " + viewer + " sent " + count + "x " + gift);
                StreamRewards.run("tiktok", findRewardKey(gift), bread, null, "%viewer_name%", viewer, "%gift_name%", gift, "%gift_id%", event.getOption("gift-id").getAsString(), "%gift_count%", count);
            }
            case "tiktok-like" -> {
                Integer count = event.getOption("like-count").getAsInteger();
                if (count == null) return;
                Viewer v = viewer(uuid, viewer);
                synchronized (v) {
                    if (v.timer != null) v.timer.cancel(false);
                    v.likes += count;
                    Synergy.getLogger().info("[TikTok] [" + bread.getName() + "] " + viewer + " +" + count + " (total: " + v.likes + ")");
                    v.timer = Tasks.later(() -> {
                        int likes;
                        synchronized (v) { likes = v.likes; v.likes = 0; }
                        String key = findThresholdKey(likes);
                        if (key == null) return;
                        Synergy.getLogger().info("[TikTok] [" + bread.getName() + "] Reward " + viewer + ": " + likes + " likes");
                        StreamRewards.run("tiktok", key, bread, null, "%viewer_name%", viewer, "%like_count%", String.valueOf(likes));
                    }, Duration.ofMillis(Config.getInt("tiktok.like-debounce-delay", 25000)));
                }
            }
            case "tiktok-follow" -> {
                Viewer v = viewer(uuid, viewer);
                long now = System.currentTimeMillis();
                if (now - v.lastFollow < Config.getInt("tiktok.follow-cooldown", 10000)) return;
                v.lastFollow = now;
                Synergy.getLogger().info("[TikTok] [" + bread.getName() + "] " + viewer + " followed");
                StreamRewards.run("tiktok", "follow", bread, null, "%viewer_name%", viewer);
            }
            case "tiktok-chat" -> {
                String message = event.getOption("message").getAsString();
                StreamRewards.run("tiktok", "chat", bread, null, "%viewer_name%", viewer, "%message%", message);
                String formatted = me.synergy.handlers.ChatHandler.formatChat("tiktok", message, viewer);
                if (formatted == null) return;
                bread.sendMessage(formatted);
                Synergy.getLogger().info("[TikTok] [" + bread.getName() + "] " + viewer + ": " + message);
            }
        }
    }

    public static void connect(UUID uuid, String username) {
        AtomicReference<LiveClient> ref = new AtomicReference<>();
        if (clients.putIfAbsent(uuid, ref) != null) return;
        BreadMaker bread = Synergy.getBread(uuid);
        LocaleBuilder.of("tiktok.live.connecting").placeholder("username", username).send(bread);
        Tasks.async(() -> {
            try {
                ref.set(TikTokLive.newClient(username)
                    .configure(settings -> settings.setFetchGifts(false))
                    .onGift((lc, e) -> stream(uuid, "tiktok-gift", e.getUser().getProfileName())
                        .setOption("gift-name", e.getGift().getName() != null ? e.getGift().getName() : "Gift#" + e.getGift().getId())
                        .setOption("gift-id", String.valueOf(e.getGift().getId()))
                        .setOption("combo-count", String.valueOf(e.getCombo())).send())
                    .onLike((lc, e) -> stream(uuid, "tiktok-like", e.getUser().getProfileName()).setOption("like-count", String.valueOf(e.getLikes())).send())
                    .onFollow((lc, e) -> stream(uuid, "tiktok-follow", e.getUser().getProfileName()).send())
                    .onComment((lc, e) -> stream(uuid, "tiktok-chat", e.getUser().getProfileName()).setOption("message", e.getText()).send())
                    .onConnected((lc, e) -> {
                        Synergy.getLogger().info("[TikTok] Connected to @" + username);
                        LocaleBuilder.of("tiktok.live.connected").placeholder("username", username).send(bread);
                    })
                    .onDisconnected((lc, e) -> {
                        if (clients.remove(uuid, ref) && ref.get() != null) LocaleBuilder.of("tiktok.live.disconnected").send(bread);
                    })
                    .onError((lc, e) -> Synergy.getLogger().warning("[TikTok] Error: " + e.getException().getMessage()))
                    .buildAndConnect());
                if (clients.get(uuid) != ref) close(ref.get());
            } catch (Exception e) {
                clients.remove(uuid, ref);
                Synergy.getLogger().warning("[TikTok] Failed to connect: " + e.getMessage());
                LocaleBuilder.of("tiktok.live.error").placeholder("message", e.getMessage()).send(bread);
            }
        });
    }

    public static void disconnect(UUID uuid) {
        AtomicReference<LiveClient> ref = clients.remove(uuid);
        if (ref == null) return;
        close(ref.get());
        LocaleBuilder.of("tiktok.live.disconnected").send(Synergy.getBread(uuid));
    }

    private static SynergyEvent stream(UUID uuid, String id, String viewer) {
        return Synergy.event(id).toPlayer(uuid).setOption("viewer-name", viewer);
    }

    private static void close(LiveClient client) {
        if (client == null) return;
        try { client.disconnect(); } catch (Exception ignored) {}
    }

    private static class Viewer {
        int likes;
        ScheduledFuture<?> timer;
        long lastFollow;
    }

    private static Viewer viewer(UUID uuid, String name) {
        return viewers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).computeIfAbsent(name, k -> new Viewer());
    }

    private static String findRewardKey(String gift) {
        return Config.getConfigurationSection("tiktok.rewards").keySet().stream()
            .filter(key -> !key.equals("default") && Config.getStringList("tiktok.rewards." + key + ".gifts").stream().anyMatch(gift::equalsIgnoreCase))
            .findFirst().orElse("default");
    }

    private static String findThresholdKey(int likes) {
        return Config.getConfigurationSection("tiktok.rewards").keySet().stream()
            .filter(key -> Config.get("tiktok.rewards." + key + ".threshold") != null && likes >= Config.getInt("tiktok.rewards." + key + ".threshold", Integer.MAX_VALUE))
            .max(Comparator.comparingInt(key -> Config.getInt("tiktok.rewards." + key + ".threshold", 0)))
            .orElse(null);
    }
}
