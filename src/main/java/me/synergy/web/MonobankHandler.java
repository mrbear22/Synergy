package me.synergy.web;

import java.util.UUID;
import java.util.Map.Entry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MonobankHandler implements SynergyListener {
    
    private static final Gson gson = new Gson();
    private static final Map<String, Long> processedTransactions = new ConcurrentHashMap<>();

    public void initialize() {
        try {
            if (!Synergy.getConfig().getBoolean("monobank.enabled")) return;
            Synergy.getEventManager().registerEvents(this);
            
            Locales.addDefault("command_description_monobank", "en", "Monobank integration");
            Locales.addDefault("command_usage_monobank", "en", new String[] {
    		    "<danger>Usage: /monobank <action> [token]",
    		    "",
    		    "<secondary>Actions:",
    		    "<primary>  link   <secondary>- Link account to Monobank",
    		    "<primary>  unlink <secondary>- Remove Monobank integration",
    		    "",
    		    "<secondary>Arguments:", 
    		    "<primary>  token <secondary>- Your Monobank API token",
    		    "",
    		    "<secondary>Examples:",
    		    "<primary>  /monobank link your_token_here",
    		    "<primary>  /monobank unlink",
    		    "",
    		    "<danger>Keep your API token secure!"
    		});
            
            Locales.addDefault("monobank_link_usage", "en", "<danger>Usage: /monobank link <token>");
            Locales.addDefault("monobank_successfully_linked", "en", "<success>Monobank account linked!");
            Locales.addDefault("monobank_already_linked", "en", "<danger>Monobank account already linked!");
            Locales.addDefault("monobank_successfully_unlinked", "en", "<success>Monobank account unlinked!");
            Locales.addDefault("monobank_not_linked", "en", "<danger>No linked Monobank account!");
            
            Synergy.getLogger().info("Monobank module initialized");
        } catch (Exception e) {
            Synergy.getLogger().error("Monobank module failed: " + e.getMessage());
        }
    }
    
    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
        if (!Synergy.getConfig().getBoolean("monobank.enabled")) return;
        if (!"monobank-donation".equals(event.getIdentifier())) return;
        if (!Synergy.isRunningSpigot()) return;

        UUID uuid = event.getPlayerUniqueId();
        if (uuid == null) return;

        BreadMaker bread = event.getBread();
        
        // Виправлено: отримуємо amount як String і парсимо до double
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

        for (Entry<String, Object> reward : Synergy.getConfig().getConfigurationSection("monobank.rewards").entrySet()) {
            int rewardCost = Synergy.getConfig().getInt("monobank.rewards." + reward.getKey() + ".cost");
            
            // Виправлено: порівнюємо з double amount
            if (amount >= rewardCost) {
                if (bestRewardKey == null || rewardCost > bestRewardCost) {
                    bestRewardKey = reward.getKey();
                    bestRewardCost = rewardCost;
                }
            }
        }

        if (bestRewardKey != null) {
            Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] Selected reward: " + bestRewardKey + " (cost: " + bestRewardCost + ")");
            
        	String inputRegex = Synergy.getConfig().getString("monobank.rewards." + bestRewardKey + ".input-regex");

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
            
            for (String command : Synergy.getConfig().getStringList("monobank.rewards." + bestRewardKey + ".commands")) {
                command = command.replace("%target_name%", bread.getName());
                command = command.replace("%target_x%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getX()));
                command = command.replace("%target_y%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getY()));
                command = command.replace("%target_z%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getZ()));
                command = command.replace("%target_world%", Synergy.getSpigot().getPlayerLocation(uuid).getWorld().getName());
                command = command.replace("%counter_name%", donaterName);
                command = command.replace("%counter_input%", comment != null ? comment : "");
                command = command.replace("%original_counter_name%", counterName != null ? counterName : "");
                command = command.replace("%amount%", String.valueOf(amount));
                command = command.replace("%description%", description != null ? description : "");
                command = command.replace("%comment%", comment != null ? comment : "");

                Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] Running command: " + command);
                Synergy.getSpigot().dispatchCommand(command);
            }
        } else {
            Synergy.getLogger().info("[Monobank] [" + bread.getName() + "] No suitable reward found for amount: " + amount + " UAH");
        }
    }
    
    public static void connect(String playerName, String token) {
        if (!WebServer.isRunning()) return;
        
        new Thread(() -> {
            try {
                URL url = new URL("https://api.monobank.ua/personal/webhook");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                connection.setRequestMethod("POST");
                connection.setRequestProperty("X-Token", token);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("webHookUrl", WebServer.getFullAddress() + "/monobank?token=" + token);
                
                try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                    writer.write(gson.toJson(requestBody));
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    Synergy.getLogger().info("Webhook registered for player: " + playerName);
                } else {
                    Synergy.getLogger().warning("Failed to register webhook for " + playerName + ": " + responseCode);
                }
                
            } catch (IOException e) {
                Synergy.getLogger().warning("Error registering webhook for " + playerName + ": " + e.getMessage());
            }
        }).start();
    }
    
    public static void disconnect(String token) {
        if (!WebServer.isRunning()) return;
        
        new Thread(() -> {
            try {
                URL url = new URL("https://api.monobank.ua/personal/webhook");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("X-Token", token);
                
                connection.getResponseCode();
                
            } catch (IOException e) {
                Synergy.getLogger().warning("Error unregistering monobank webhook: " + e.getMessage());
            }
        }).start();
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

            // Пропускаємо негативні транзакції (списання) та вже оброблені
            if (processedTransactions.containsKey(transactionId) || amountInKopecks <= 0) {
                return;
            }

            processedTransactions.put(transactionId, timestamp);
            cleanOldTransactions();
            
            // Конвертуємо копійки в гривні
            double amountInUAH = amountInKopecks / 100.0;
            
            UUID uuid = Synergy.getDataManager().findUserUUID("monobank", token);
            BreadMaker bread = Synergy.getBread(uuid);
            if (bread != null) {
                handleDonation(bread.getName(), uuid, amountInUAH, description, comment, counterName);
            } else {
                Synergy.getLogger().warning("[Monobank] User not found for token: " + token.substring(0, Math.min(10, token.length())) + "...");
            }
            
        } catch (Exception e) {
            Synergy.getLogger().warning("Error parsing transaction data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleDonation(String playerName, UUID playerUuid, double amount, String description, String comment, String counterName) {
        Synergy.getLogger().info("[Monobank] Processing donation: " + playerName + " - " + amount + " UAH from " + counterName);
        
        Synergy.event("monobank-donation")
            .setPlayerUniqueId(playerUuid)
            .setOption("player-name", playerName)
            .setOption("amount", String.valueOf(amount)) // Передаємо як String
            .setOption("description", description != null ? description : "")
            .setOption("comment", comment != null ? comment : "")
            .setOption("counter-name", counterName != null ? counterName : "")
            .send();
    }
    
    private static void cleanOldTransactions() {
        long currentTime = System.currentTimeMillis() / 1000;
        long dayAgo = currentTime - 86400; // 24 години тому
        
        processedTransactions.entrySet().removeIf(entry -> entry.getValue() < dayAgo);
    }
}