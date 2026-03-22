package me.synergy.web;

import java.util.*;
import java.util.concurrent.*;

import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.live.LiveClient;
import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.ChatHandler;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.LocaleBuilder;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Chat;
import me.synergy.utils.Utils;

public class TikTokHandler implements SynergyListener {

    private static final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public void initialize() {
        try {
            if (!Config.addDefault("tiktok.enabled", false)) return;

            Config.addDefault("tiktok.listener", false);
            Config.addDefault("tiktok.follow-cooldown", 10000);
            Config.addDefault("tiktok.like-debounce-delay", 25000);

            if (Config.getConfigurationSection("tiktok.rewards").isEmpty()) {
                Config.addDefault("tiktok.rewards.small.title", "Small Gift");
                Config.addDefault("tiktok.rewards.small.gifts", new String[] {"Rose", "TikTok", "Heart", "GG"});
                Config.addDefault("tiktok.rewards.small.commands", new String[] {
                    "say &e%viewer_name% &7sent &e%gift_count%x %gift_name% &7to &e%streamer_name%!"
                });

                Config.addDefault("tiktok.rewards.default.title", "Gift");
                Config.addDefault("tiktok.rewards.default.commands", new String[] {
                    "say &e%viewer_name% &7sent &e%gift_count%x %gift_name%&7!"
                });

                Config.addDefault("tiktok.rewards.small-like.title", "Small Like");
                Config.addDefault("tiktok.rewards.small-like.threshold", 1);
                Config.addDefault("tiktok.rewards.small-like.commands", new String[] {
                    "say &b%viewer_name% &7sent &b%like_count% likes!"
                });

                Config.addDefault("tiktok.rewards.follow.title", "Follow");
                Config.addDefault("tiktok.rewards.follow.commands", new String[] {
                    "say &d%viewer_name% &7followed!"
                });

                Config.addDefault("tiktok.rewards.chat.title", "Chat");
                Config.addDefault("tiktok.rewards.chat.commands", new String[] {
                    "say &7[TikTok] &f%viewer_name%&7: %message%"
                });
            }

            Locales.addDefault("tiktok-connecting",        "en", "&aConnecting to @%username%...");
            Locales.addDefault("tiktok-connected",         "en", "&aConnected to @%username%!");
            Locales.addDefault("tiktok-disconnected",      "en", "&eDisconnected from @%username%");
            Locales.addDefault("tiktok-disconnected-bare", "en", "&eDisconnected");
            Locales.addDefault("tiktok-error",             "en", "&cTikTok error: %message%");
            Locales.addDefault("tiktok-failed",            "en", "&cFailed to connect: %message%");

            Synergy.getEventManager().registerEvents(this);
            Synergy.getLogger().info("TikTok module initialized");
        } catch (Exception e) {
            Synergy.getLogger().error("TikTok module failed: " + e.getMessage());
        }
    }

    private static class PlayerSession {
        LiveClient client;
        Map<String, ViewerSession> viewers = new ConcurrentHashMap<>();

        ViewerSession getViewer(String name) {
            return viewers.computeIfAbsent(name, ViewerSession::new);
        }

        void clear() {
            viewers.values().forEach(ViewerSession::cancel);
            viewers.clear();
            if (client != null) {
                try { client.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private static class ViewerSession {
        @SuppressWarnings("unused")
        final String name;
        int likeTotal = 0;
        ScheduledFuture<?> likeTimer;
        long lastFollow = 0;

        ViewerSession(String name) { this.name = name; }

        void cancel() {
            if (likeTimer != null && !likeTimer.isDone()) likeTimer.cancel(false);
        }
    }

    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
        if (!Config.getBoolean("tiktok.enabled")) return;

        String id = event.getIdentifier();
        UUID uuid = event.getPlayerUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);

        if (Synergy.isRunningSpigot()) {
        	
        	if (!bread.isOnline()) return;
        	
            switch (id) {
                case "tiktok-gift" -> {
                    String gift = event.getOption("gift-name").getAsString();
                    String giftIdStr = event.getOption("gift-id").getAsString();
                    String viewer = event.getOption("viewer-name").getAsString();
                    int count = event.getOption("combo-count").getAsInteger();
                    
                    Synergy.getLogger().info(String.format("[TikTok] [%s] %s sent %dx %s", bread.getName(), viewer, count, gift));
                    execute(uuid, findRewardKey(gift, "gifts", "default"), Map.of(
                        "%viewer_name%", viewer,
                        "%gift_name%", gift,
                        "%gift_id%", giftIdStr,
                        "%gift_count%", String.valueOf(count)
                    ));

                }
                case "tiktok-like" -> {
                    String viewer = event.getOption("viewer-name").getAsString();
                    try {
                        int count = Integer.parseInt(event.getOption("like-count").getAsString());
                        ViewerSession vs = sessions.computeIfAbsent(uuid, k -> new PlayerSession()).getViewer(viewer);
                        vs.cancel();
                        vs.likeTotal += count;
                        Synergy.getLogger().info(String.format("[TikTok] [%s] %s +%d (total: %d)", bread.getName(), viewer, count, vs.likeTotal));
                        long delay = Config.getInt("tiktok.like-debounce-delay", 25000);
                        vs.likeTimer = scheduler.schedule(() -> {
                            String key = findThresholdKey(vs.likeTotal);
                            if (key != null) {
                                Synergy.getLogger().info(String.format("[TikTok] [%s] Reward %s: %d likes", bread.getName(), viewer, vs.likeTotal));
                                execute(uuid, key, Map.of("%viewer_name%", viewer, "%like_count%", String.valueOf(vs.likeTotal)));
                            }
                            vs.likeTotal = 0;
                        }, delay, TimeUnit.MILLISECONDS);
                    } catch (NumberFormatException ignored) {}
                }
                case "tiktok-follow" -> {
                    String viewer = event.getOption("viewer-name").getAsString();
                    ViewerSession vs = sessions.computeIfAbsent(uuid, k -> new PlayerSession()).getViewer(viewer);
                    long now = System.currentTimeMillis();
                    long cooldown = Config.getInt("tiktok.follow-cooldown", 10000);
                    if (now - vs.lastFollow >= cooldown) {
                        vs.lastFollow = now;
                        Synergy.getLogger().info(String.format("[TikTok] [%s] %s followed", bread.getName(), viewer));
                        execute(uuid, "follow", Map.of("%viewer_name%", viewer));
                    }
                }
                case "tiktok-chat" -> {
                    String viewer = event.getOption("viewer-name").getAsString();
                    String message = event.getOption("message").getAsString();
                    execute(uuid, "chat", Map.of("%viewer_name%", viewer));
                    
                    var chat = new Chat("tiktok");
                    if (!chat.isEnabled()) return;
                    
                    bread.sendMessage(ChatHandler.formatMessage(chat, new ChatHandler.Message(message), viewer));
                    Synergy.getLogger().info("[TikTok] [" + bread.getName() + "] " + viewer + ": " + message);
                }
            }
        }

        if (!Config.getBoolean("tiktok.listener")) return;

        switch (id) {
            case "tiktok-connect"        -> connect(uuid, event.getOption("username").getAsString());
            case "tiktok-disconnect"     -> disconnect(uuid);
            case "tiktok-connect-all"    -> connectAll();
            case "tiktok-disconnect-all" -> disconnectAll();
        }
    }

    public static void connect(UUID uuid, String username) {
        if (!Config.getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-connect").setPlayerUniqueId(uuid).setOption("username", username).send();
            return;
        }

        PlayerSession session = sessions.computeIfAbsent(uuid, k -> new PlayerSession());
        if (session.client != null) return;

        BreadMaker bread = Synergy.getBread(uuid);
        bread.sendMessage(LocaleBuilder.of("tiktok-connecting").placeholder("username", username).build());

        new Thread(() -> {
            try {
                session.client = TikTokLive.newClient(username)
                	.configure(settings -> {settings.setFetchGifts(false);})
                	.onGift((lc, e) -> Synergy.event("tiktok-gift").setPlayerUniqueId(uuid)
        			    .setOption("viewer-name", e.getUser().getProfileName())
        			    .setOption("gift-name", e.getGift().getName() != null ? e.getGift().getName() : "Gift#" + e.getGift().getId())
        			    .setOption("gift-id", String.valueOf(e.getGift().getId()))
        			    .setOption("combo-count", String.valueOf(e.getCombo())).send())
        			.onLike((lc, e) -> Synergy.event("tiktok-like").setPlayerUniqueId(uuid)
        			    .setOption("viewer-name", e.getUser().getProfileName())
        			    .setOption("like-count", String.valueOf(e.getLikes())).send())
        			.onFollow((lc, e) -> Synergy.event("tiktok-follow").setPlayerUniqueId(uuid)
        			    .setOption("viewer-name", e.getUser().getProfileName()).send())
        			.onComment((lc, e) -> Synergy.event("tiktok-chat").setPlayerUniqueId(uuid)
        			    .setOption("viewer-name", e.getUser().getProfileName())
        			    .setOption("account-name", username)
        			    .setOption("message", e.getText()).send())
                    .onConnected((lc, e) -> {
                        Synergy.getLogger().info("[TikTok] Connected to @" + username);
                        bread.sendMessage(LocaleBuilder.of("tiktok-connected").placeholder("username", username).build());
                    })
                    .onDisconnected((lc, e) -> {
                        sessions.remove(uuid);
                        bread.sendMessage(LocaleBuilder.of("tiktok-disconnected").placeholder("username", username).build());
                    })
                    .onError((lc, e) -> {
                        Synergy.getLogger().warning("[TikTok] Error: " + e.getException().getMessage());
                        bread.sendMessage(LocaleBuilder.of("tiktok-error").placeholder("message", e.getException().getMessage()).build());
                    })
                    .buildAndConnect();
            } catch (Exception e) {
                sessions.remove(uuid);
                Synergy.getLogger().warning("[TikTok] Failed to connect: " + e.getMessage());
                bread.sendMessage(LocaleBuilder.of("tiktok-failed")
                    .placeholder("message", e.getMessage()).build());
            }
        }).start();
    }

    public static void disconnect(UUID uuid) {
        if (!Config.getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-disconnect").setPlayerUniqueId(uuid).send();
            return;
        }
        PlayerSession session = sessions.remove(uuid);
        if (session != null) {
            session.clear();
            Synergy.getBread(uuid).sendMessage(LocaleBuilder.of("tiktok-disconnected-bare").build());
        }
    }

    public static void disconnectAll() {
        if (!Config.getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-disconnect-all").send();
            return;
        }
        sessions.values().forEach(PlayerSession::clear);
        sessions.clear();
    }

    private static void connectAll() {
        if (!Config.getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-connect-all").send();
            return;
        }
        new Thread(() -> {
            Utils.getOnlinePlayerUUIDs().forEach(uuid -> {
                BreadMaker bread = Synergy.getBread(uuid);
                if (bread.getData("tiktok").isSet()) {
                    String username = bread.getData("tiktok").getAsString();
                    if (username != null && !username.isEmpty()) {
                        try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                        connect(uuid, username);
                    }
                }
            });
        }).start();
    }

    private void execute(UUID uuid, String rewardKey, Map<String, String> placeholders) {
        List<String> commands = Config.getStringList("tiktok.rewards." + rewardKey + ".commands");
        if (commands == null) return;
        BreadMaker bread = Synergy.getBread(uuid);
        commands.forEach(cmd -> {
            String result = cmd
                .replace("%streamer_name%", bread.getName())
                .replace("%streamer_uuid%", uuid.toString())
                .replace("%streamer_uuid_nbt%", uuidToIntArray(uuid));
            for (var entry : placeholders.entrySet()) result = result.replace(entry.getKey(), entry.getValue());
            Synergy.getSpigot().dispatchCommand(result);
        });
    }

    private static String uuidToIntArray(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return "[I;" + (int)(msb >> 32) + "," + (int) msb + "," + (int)(lsb >> 32) + "," + (int) lsb + "]";
    }
    
    private String findRewardKey(String value, String field, String fallback) {
        return Config.getConfigurationSection("tiktok.rewards")
            .entrySet().stream()
            .filter(e -> !fallback.equals(e.getKey()))
            .filter(e -> {
                List<String> list = Config.getStringList("tiktok.rewards." + e.getKey() + "." + field);
                return list != null && list.stream().anyMatch(v -> v.equalsIgnoreCase(value));
            })
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(fallback);
    }

    private String findThresholdKey(int value) {
        return Config.getConfigurationSection("tiktok.rewards")
            .entrySet().stream()
            .filter(e -> Config.get("tiktok.rewards." + e.getKey() + ".threshold") != null)
            .filter(e -> value >= Config.getInt("tiktok.rewards." + e.getKey() + ".threshold", Integer.MAX_VALUE))
            .max(Comparator.comparingInt(e -> Config.getInt("tiktok.rewards." + e.getKey() + ".threshold", 0)))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}