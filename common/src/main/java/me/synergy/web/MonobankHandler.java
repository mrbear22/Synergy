package me.synergy.web;

import me.synergy.modules.DataManager;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.io.InputStreamReader;

import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.StreamRewards;
import me.synergy.modules.Config;
import me.synergy.modules.Http;
import me.synergy.modules.Module;
import me.synergy.objects.BreadMaker;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MonobankHandler implements Module {

    private static final String WEBHOOK = "https://api.monobank.ua/personal/webhook";
    
    private static final Gson gson = new Gson();
    private static final Map<String, Long> processedTransactions = new ConcurrentHashMap<>();

    @Override public String toggle() { return "monobank.enabled"; }

    @Override
    public void defaults() {
        Config.addDefault("monobank.enabled", false);
        if (!Synergy.isProxy() && Config.getConfigurationSection("monobank.rewards").isEmpty()) {
            Config.addDefault("monobank.rewards.wither.title", "Wither");
            Config.addDefault("monobank.rewards.wither.description", "Spawn a Wither");
            Config.addDefault("monobank.rewards.wither.cost", 1000);
            Config.addDefault("monobank.rewards.wither.input-required", false);
            Config.addDefault("monobank.rewards.wither.input-regex", "[a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9]");
            Config.addDefault("monobank.rewards.wither.commands", new String[] {"execute at %streamer_name% run summon minecraft:wither ~ ~2 ~", "execute at %streamer_name% run title @a[distance=..500] title {\"text\":\"%counter_name% has spawned a Wither\"}"});
        }

    }

    @Override
    public void start() {
        Synergy.getEventManager().registerEvents(MonobankHandler.class);
    }
    
    @Override
    public void join(BreadMaker bread) {
        String token = bread.getData("monobank").getAsString();
        if (Synergy.isHub() && token != null) connect(bread.getName(), token);
    }

    @Override
    public void quit(BreadMaker bread) {
        String token = bread.getData("monobank").getAsString();
        if (Synergy.isHub() && token != null) disconnect(token);
    }

    /** Asks the hub to register (link) or remove (unlink) the Monobank webhook for this token. */
    public static void request(UUID uuid, String token, boolean link) {
        Synergy.event(link ? "monobank-connect" : "monobank-disconnect").toProxy().setPlayerUniqueId(uuid).setOption("token", token).send();
    }

    @SynergyHandler({"monobank-connect", "monobank-disconnect"})
    public static void onControl(SynergyEvent event) {
        if (!Synergy.isHub()) return;
        String token = event.getOption("token").getAsString();
        if (event.getIdentifier().equals("monobank-connect")) connect(event.getBread().getName(), token);
        else disconnect(token);
    }

    @SynergyHandler("monobank-donation")
    public static void onSynergyEvent(SynergyEvent event) {
        UUID uuid = event.getPlayerUniqueId();
        BreadMaker bread = event.getBread();
        if (Synergy.isProxy() || uuid == null || !bread.isOnline()) return;
        
        String amountStr = event.getOption("amount").getAsString();
        if (amountStr == null) {
            Synergy.getLogger().warning("[Monobank] Amount is null for player: " + bread.getName());
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Synergy.getLogger().warning("[Monobank] Invalid amount format: " + amountStr);
            return;
        }
        
        String counterName = event.getOption("counter-name").getAsString();
        String description = event.getOption("description").getAsString();
        String comment = event.getOption("comment").getAsString();

        String donaterName = (counterName != null && !counterName.trim().isEmpty()) ? counterName : comment != null ? comment : "🐈";

        Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] " + donaterName + " donated: " + amount + " UAH");
        Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] Description: " + (description != null ? description : "") + " Comment: " + (comment != null ? comment : ""));

        String bestRewardKey = null;
        int bestRewardCost = 0;

        for (Entry<String, Object> reward : Config.getConfigurationSection("monobank.rewards").entrySet()) {
            int rewardCost = Config.getInt("monobank.rewards." + reward.getKey() + ".cost");
            
            if (amount >= rewardCost) {
                if (bestRewardKey == null || rewardCost > bestRewardCost) {
                    bestRewardKey = reward.getKey();
                    bestRewardCost = rewardCost;
                }
            }
        }

        if (bestRewardKey == null) {
            Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] No suitable reward found for amount: " + amount + " UAH");
            return;
        }
        Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] Selected reward: " + bestRewardKey + " (cost: " + bestRewardCost + ")");
        StreamRewards.run("monobank", bestRewardKey, bread, comment, "%counter_name%", donaterName, "%counter_input%", comment, "%original_counter_name%", counterName,
            "%amount%", String.valueOf(amount), "%description%", description, "%comment%", comment);
    }
    
    public static void connect(String playerName, String token) {
        if (!WebServer.isRunning()) return;
        JsonObject body = new JsonObject();
        body.addProperty("webHookUrl", WebServer.getFullAddress() + "/monobank?token=" + token);
        Http.post(WEBHOOK).header("X-Token", token).json(body).async().whenComplete((response, error) -> {
            if (error != null || !response.ok()) Synergy.getLogger().warning("Monobank webhook for " + playerName + " failed: " + (error != null ? error.getMessage() : response.status()));
        });
    }

    public static void disconnect(String token) {
        if (WebServer.isRunning()) Http.delete(WEBHOOK).header("X-Token", token).async();
    }

    @SuppressWarnings("restriction")
	public static class WebhookHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody())) {
                    StringBuilder requestBody = new StringBuilder();
                    char[] buffer = new char[1024];
                    int bytesRead;
                    while ((bytesRead = reader.read(buffer)) != -1) {
                        requestBody.append(buffer, 0, bytesRead);
                    }
                    
                    String query = exchange.getRequestURI().getQuery();
                    String token = null;
                    if (query != null && query.startsWith("token=")) {
                        token = query.substring(6);
                    }
                    
                    final String finalToken = token;
                    
                    if (finalToken != null) {
                        processTransaction(requestBody.toString(), finalToken);
                    }
                    
                    exchange.sendResponseHeaders(200, -1);
                } catch (Exception e) {
                    Synergy.getLogger().warning("Error processing webhook: " + e.getMessage());
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    
    private static void processTransaction(String jsonData, String token) {
        try {
            JsonObject root = JsonParser.parseString(jsonData).getAsJsonObject();
            
            if (!root.has("type") || !root.has("data")) return;
            
            String type = root.get("type").getAsString();
            if (!"StatementItem".equals(type)) return;
            
            JsonObject data = root.getAsJsonObject("data");
            JsonObject statementItem = data.getAsJsonObject("statementItem");
            
            if (statementItem == null) return;
            
            String transactionId = statementItem.get("id").getAsString();
            long amountInKopecks = statementItem.get("amount").getAsLong();
            String description = statementItem.has("description") ? 
                statementItem.get("description").getAsString() : "";
            String comment = statementItem.has("comment") ? 
                statementItem.get("comment").getAsString() : "";
            String counterName = statementItem.has("counterName") ? 
                statementItem.get("counterName").getAsString() : "";
            long timestamp = statementItem.get("time").getAsLong();

            if (processedTransactions.containsKey(transactionId) || amountInKopecks <= 0) {
                return;
            }

            processedTransactions.put(transactionId, timestamp);
            cleanOldTransactions();
            
            double amountInUAH = amountInKopecks / 100.0;
            
            UUID uuid = DataManager.findUserUUID("monobank", token);
            BreadMaker bread = Synergy.getBread(uuid);
            if (bread != null) {
                handleDonation(bread.getName(), uuid, amountInUAH, description, comment, counterName);
            } else {
                Synergy.getLogger().warning("[Monobank] User not found for token: " + token.substring(0, Math.min(10, token.length())) + "...");
            }
            
        } catch (Exception e) {
            Synergy.getLogger().warning("Error parsing transaction data: " + e.getMessage());
            Synergy.getLogger().error("MonobankHandler: " + e, e);
        }
    }
    
    private static void handleDonation(String playerName, UUID playerUuid, double amount, String description, String comment, String counterName) {
        Synergy.getLogger().info("[Monobank] Processing donation: " + playerName + " - " + amount + " UAH from " + counterName);
        
        Synergy.event("monobank-donation")
            .toPlayer(playerUuid)
            .setOption("player-name", playerName)
            .setOption("amount", String.valueOf(amount))
            .setOption("description", description != null ? description : "")
            .setOption("comment", comment != null ? comment : "")
            .setOption("counter-name", counterName != null ? counterName : "")
            .send();
    }
    
    private static void cleanOldTransactions() {
        long currentTime = System.currentTimeMillis() / 1000;
        long dayAgo = currentTime - 86400;
        
        processedTransactions.entrySet().removeIf(entry -> entry.getValue() < dayAgo);
    }
}