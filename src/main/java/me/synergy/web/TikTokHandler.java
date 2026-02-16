package me.synergy.web;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.live.LiveClient;
import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Utils;

public class TikTokHandler implements SynergyListener {
    
    private static final Map<UUID, LiveClient> activeConnections = new ConcurrentHashMap<>();
    private static final Map<String, Long> processedGifts = new ConcurrentHashMap<>();

    public void initialize() {
        try {
            if (!Synergy.getConfig().getBoolean("tiktok.enabled")) return;
            Synergy.getEventManager().registerEvents(this);
            connectAll();
            Synergy.getLogger().info("TikTok module initialized");
        } catch (Exception e) {
            Synergy.getLogger().error("TikTok module failed: " + e.getMessage());
        }
    }

    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
        if (!Synergy.getConfig().getBoolean("tiktok.enabled")) return;
        
        if ("tiktok-gift".equals(event.getIdentifier()) && Synergy.isRunningSpigot()) {
            BreadMaker bread = event.getBread();
            String giftName = event.getOption("gift-name").getAsString();
            int comboCount = parseComboCount(event.getOption("combo-count").getAsString());
            if (comboCount == 0) return;
            String donaterName = event.getOption("donater-name").getAsString();
            Synergy.getLogger().info(String.format("[TikTok] [%s] %s sent %dx %s", bread.getName(), donaterName, comboCount, giftName));
            executeReward(event.getPlayerUniqueId(), giftName, event.getOption("gift-id").getAsString(), donaterName, comboCount);
        }

        if (!Synergy.getConfig().getBoolean("tiktok.listener")) return;
        
        if ("tiktok-connect".equals(event.getIdentifier())) {
            connect(event.getPlayerUniqueId(), event.getOption("username").getAsString());
        }
        if ("tiktok-disconnect".equals(event.getIdentifier())) {
            disconnect(event.getPlayerUniqueId());
        }
        if ("tiktok-connect-all".equals(event.getIdentifier())) {
            connectAll();
        }
        if ("tiktok-disconnect-all".equals(event.getIdentifier())) {
            disconnectAll();
        }
    }
    
    private int parseComboCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Synergy.getLogger().warning("[TikTok] Invalid combo count");
            return 0;
        }
    }
    
    private void executeReward(UUID uuid, String giftName, String giftId, String donaterName, int comboCount) {
        BreadMaker bread = Synergy.getBread(uuid);
        String rewardKey = findRewardKey(giftName);
        Synergy.getConfig().getStringList("tiktok.rewards." + rewardKey + ".commands")
            .forEach(cmd -> Synergy.getSpigot().dispatchCommand(
                cmd.replace("%target_name%", bread.getName())
                   .replace("%target_x%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getX()))
                   .replace("%target_y%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getY()))
                   .replace("%target_z%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getZ()))
                   .replace("%target_world%", Synergy.getSpigot().getPlayerLocation(uuid).getWorld().getName())
                   .replace("%donater_name%", donaterName)
                   .replace("%gift_name%", giftName)
                   .replace("%gift_id%", giftId)
                   .replace("%combo_count%", String.valueOf(comboCount))
            ));
    }
    
    private String findRewardKey(String giftName) {
        return Synergy.getConfig().getConfigurationSection("tiktok.rewards")
            .entrySet().stream()
            .filter(e -> !"default".equals(e.getKey()))
            .filter(e -> Synergy.getConfig().getStringList("tiktok.rewards." + e.getKey() + ".gifts")
                .stream().anyMatch(g -> g.equalsIgnoreCase(giftName)))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("default");
    }
    
    public static void connect(UUID playerUuid, String username) {
        if (!Synergy.getConfig().getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-connect").setPlayerUniqueId(playerUuid).setOption("username", username).send();
            return;
        }
        if (activeConnections.containsKey(playerUuid)) return;
        BreadMaker bread = Synergy.getBread(playerUuid);
        new Thread(() -> {
            try {
                LiveClient client = TikTokLive.newClient(username)
                    .onGift((lc, e) -> processGift(playerUuid, e.getGift().getId(), e.getGift().getName(), 1, e.getUser().getProfileName()))
                    .onGiftCombo((lc, e) -> processGift(playerUuid, e.getGift().getId(), e.getGift().getName(), e.getCombo(), e.getUser().getProfileName()))
                    .onError((lc, e) -> Synergy.getLogger().warning("[TikTok] Error for " + bread.getName() + ": " + e.getException().getMessage()))
                    .onConnected((lc, e) -> Synergy.getLogger().info("[TikTok] Connected to @" + username + " for " + bread.getName()))
                    .onDisconnected((lc, e) -> activeConnections.remove(playerUuid))
                    .buildAndConnect();
                activeConnections.putIfAbsent(playerUuid, client);
            } catch (Exception e) {
                Synergy.getLogger().warning("[TikTok] Failed to connect for " + bread.getName() + ": " + e.getMessage());
            }
        }).start();
    }
    
    private static void processGift(UUID playerUuid, int giftId, String giftName, int comboCount, String donaterName) {
        BreadMaker bread = Synergy.getBread(playerUuid);
        String uniqueId = String.format("%s-%d-%s-%d", playerUuid, giftId, donaterName, comboCount);
        long now = System.currentTimeMillis();
        Long lastSent = processedGifts.get(uniqueId);
        if (lastSent != null && now - lastSent < 3000) return;
        processedGifts.put(uniqueId, now);
        processedGifts.entrySet().removeIf(e -> e.getValue() < now - 60000);
        Synergy.event("tiktok-gift")
            .setPlayerUniqueId(playerUuid)
            .setOption("player-name", bread.getName())
            .setOption("gift-name", giftName)
            .setOption("gift-id", String.valueOf(giftId))
            .setOption("combo-count", String.valueOf(comboCount))
            .setOption("donater-name", donaterName)
            .send();
    }
    
    public static void disconnect(UUID playerUuid) {
        if (!Synergy.getConfig().getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-disconnect").setPlayerUniqueId(playerUuid).send();
            return;
        }
        Stream.ofNullable(activeConnections.remove(playerUuid)).forEach(client -> {
            try { client.disconnect(); } 
            catch (Exception e) { Synergy.getLogger().warning("[TikTok] Error disconnecting: " + e.getMessage()); }
        });
    }
    
    public static void disconnectAll() {
        if (!Synergy.getConfig().getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-disconnect-all").send();
            return;
        }
        activeConnections.values().forEach(client -> {
            try { client.disconnect(); } catch (Exception e) { }
        });
        activeConnections.clear();
    }
    
    private static void connectAll() {
        if (!Synergy.getConfig().getBoolean("tiktok.listener")) {
            Synergy.event("tiktok-connect-all").send();
            return;
        }
        new Thread(() -> {
            Utils.getOnlinePlayerUUIDs().forEach(uuid -> {
                BreadMaker bread = Synergy.getBread(uuid);
                if (bread.getData("tiktok").isSet()) {
                    String username = bread.getData("tiktok").getAsString();
                    if (username != null && !username.isEmpty()) {
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        connect(uuid, username);
                    }
                }
            });
        }).start();
    }
}