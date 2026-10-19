package me.synergy.web;

import me.synergy.modules.DataManager;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;
import java.io.IOException;
import java.io.InputStreamReader;

import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
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
    
    @SynergyHandler("monobank-donation")
    public static void onSynergyEvent(SynergyEvent event) {
        if (!Config.getBoolean("monobank.enabled") || Synergy.isProxy()) return;

        UUID uuid = event.getPlayerUniqueId();
        if (uuid == null) return;

        BreadMaker bread = event.getBread();
        
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

        if (bestRewardKey != null) {
            Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] Selected reward: " + bestRewardKey + " (cost: " + bestRewardCost + ")");
            
        	String inputRegex = Config.getString("monobank.rewards." + bestRewardKey + ".input-regex");

        	if (inputRegex != null && !inputRegex.isEmpty() && comment != null && !comment.isEmpty()) {
        	    try {
        	        if (!comment.matches(inputRegex)) {
        	            Synergy.getLogger().warning("[Monobank] ["+counterName+"] Invalid input from " + counterName + 
        	                ". Input: '" + counterName + "' doesn't match regex: '" + inputRegex + "'");
        	            
        	            return;
        	        } else {
        	            Synergy.getLogger().info("[Monobank] ["+bread.getName()+"] Input validation passed for " + counterName);
        	        }
        	    } catch (PatternSyntaxException e) {
        	        Synergy.getLogger().error("[Monobank] ["+bread.getName()+"] Invalid regex pattern: " + inputRegex +  ". Error: " + e.getMessage());
        	        return;
        	    }
        	}
            
            for (String command : Config.getStringList("monobank.rewards." + bestRewardKey + ".commands")) {
                command = command.replace("%streamer_name%", bread.getName());
                command = Synergy.platform().position(uuid).orElseThrow().apply(command, "target");
                command = command.replace("%counter_name%", donaterName);
                command = command.replace("%counter_input%", comment != null ? comment : "");
                command = command.replace("%original_counter_name%", counterName != null ? counterName : "");
                command = command.replace("%amount%", String.valueOf(amount));
                command = command.replace("%description%", description != null ? description : "");
                command = command.replace("%comment%", comment != null ? comment : "");

                Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] Running command: " + command);
                Synergy.platform().console(command);
            }
        } else {
            Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] No suitable reward found for amount: " + amount + " UAH");
        }
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
            .setPlayerUniqueId(playerUuid)
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