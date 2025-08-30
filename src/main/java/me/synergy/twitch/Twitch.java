package me.synergy.twitch;

import java.util.Map.Entry;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;

public class Twitch implements SynergyListener {

    public void initialize() {
        try {
            if (!Synergy.getConfig().getBoolean("twitch.enabled")) return;
            Synergy.getEventManager().registerEvents(this);
            Synergy.getLogger().info("Twitch module initialized");
        } catch (Exception e) {
            Synergy.getLogger().error("Twitch module failed: " + e.getMessage());
        }
    }
    
    public static ConnectionManager getConnectionManager() {
    	return new ConnectionManager();
    }
    
    public static LinksManager getLinksManager() {
    	return new LinksManager();
    }
    
    public static RewardManager getRewardsManager(String channelName, String accessToken) {
    	return new RewardManager(channelName, accessToken);
    }
    
    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
        if (!Synergy.getConfig().getBoolean("twitch.enabled")) return;

        UUID uuid = event.getPlayerUniqueId();
        
        if (uuid == null) return;
        
        String eventId = event.getIdentifier();
        String channelName = Synergy.getBread(uuid).getDataOrDefault("twitch-username", null).getAsString();
        String accessToken = Synergy.getBread(uuid).getDataOrDefault("twitch-token", null).getAsString();
        
        switch (eventId) {
            case "make-twitch-link":
            	if (Synergy.getConfig().getString("twitch.client-id").equals("client-id")) return;
                String username = event.getOption("tag").getAsString();
                String token = event.getOption("token").getAsString();
                getLinksManager().linkAccount(uuid, username, token);
                break;
                
            case "remove-twitch-link":
            	if (Synergy.getConfig().getString("twitch.client-id").equals("client-id")) return;
            	getLinksManager().unlinkAccount(uuid);
                break;
                
            case "create-twitch-reward":
            	if (Synergy.getConfig().getString("twitch.client-id").equals("client-id")) return;
                if (channelName != null && accessToken != null) {
                	try {
						getRewardsManager(channelName, accessToken).create(
						    event.getOption("title").getAsString(),
						    event.getOption("cost").getAsInteger(),
						    event.getOption("description").getAsString(),
						    event.getOption("input-required").getAsBoolean());
					} catch (Exception e) {
						event.getBread().sendMessage(e.getMessage());
					}
                }
                break;
                
            case "remove-twitch-reward":
            	if (Synergy.getConfig().getString("twitch.client-id").equals("client-id")) return;
                if (channelName != null && accessToken != null) {
                	try {
						getRewardsManager(channelName, accessToken).delete(event.getOption("title").getAsString());
					} catch (Exception e) {
						event.getBread().sendMessage(e.getMessage());
					}
                }
                break;
                
            case "send-twitch-message":
            	if (Synergy.getConfig().getString("twitch.client-id").equals("client-id")) return;
                if (channelName != null) {
                    ChatHandler.sendMessage(channelName, event.getOption("message").getAsString());
                }
                break;
                
            case "twitch-reward-redeemed":
            	BreadMaker bread = event.getBread();
            	String rewardTitle = event.getOption("reward-title").getAsString();
            	String viewerName = event.getOption("viewer-name").getAsString();
            	String rewardId = event.getOption("reward-id").getAsString();
            	String viewerInput = event.getOption("viewer-input").getAsString();

            	Synergy.getLogger().info("[Twitch] ["+channelName+"] " + viewerName + " redeemed: " + rewardTitle);

            	if (Synergy.isRunningSpigot()) {

            	    for (Entry<String, Object> reward : Synergy.getConfig().getConfigurationSection("twitch.rewards").entrySet()) {
            	        
            	        if (Synergy.getConfig().getString("twitch.rewards." + reward.getKey() + ".title").equalsIgnoreCase(rewardTitle)) {
            	        	
            	        	String inputRegex = Synergy.getConfig().getString("twitch.rewards." + reward.getKey() + ".input-regex");

                        	if (inputRegex != null && !inputRegex.isEmpty() && viewerInput != null && !viewerInput.isEmpty()) {
                        	    try {
                        	        if (!viewerInput.matches(inputRegex)) {
                        	            Synergy.getLogger().warning("[Twitch] ["+channelName+"] Invalid input from " + viewerName + 
                        	                ". Input: '" + viewerInput + "' doesn't match regex: '" + inputRegex + "'");
                        	            
                        	            Synergy.createSynergyEvent("send-twitch-message")
            								.setPlayerUniqueId(bread.getUniqueId())
            								.setOption("message", "<lang>twitch-input-doesnt-match-regex</lang>")
            								.setOption("twitch-channel", channelName)
            								.send();
                        	            
                        	            break;
                        	        } else {
                        	            Synergy.getLogger().info("[Twitch] ["+channelName+"] Input validation passed for " + viewerName);
                        	        }
                        	    } catch (PatternSyntaxException e) {
                        	        Synergy.getLogger().error("[Twitch] ["+channelName+"] Invalid regex pattern: " + inputRegex +  ". Error: " + e.getMessage());
                        	        break;
                        	    }
                        	}
            	        	
            	            for (String command : Synergy.getConfig().getStringList("twitch.rewards."+reward.getKey()+".commands")) {

            	                command = command.replace("%streamer_name%", bread.getName());
            	                command = command.replace("%streamer_x%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getX()));
            	                command = command.replace("%streamer_y%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getY()));
            	                command = command.replace("%streamer_z%", String.valueOf(Synergy.getSpigot().getPlayerLocation(uuid).getZ()));
            	                command = command.replace("%streamer_world%", Synergy.getSpigot().getPlayerLocation(uuid).getWorld().getName());

            	                command = command.replace("%viewer_name%", viewerName);
            	                command = command.replace("%reward_title%", rewardTitle);
            	                command = command.replace("%viewer_input%", viewerInput);
            	                command = command.replace("%reward_id%", rewardId);

            	            	Synergy.getLogger().info("[Twitch] ["+channelName+"] " + "running command: " + command);
            	                
            	                Synergy.getSpigot().dispatchCommand(command);
            	            }
            	            break;
            	        }
            	    }
            	}
                
                break;
                
                
        }
    }
    
    public static void shutdownAll() {
    	ConnectionManager.channelClients.forEach((channelName, client) -> {
            try {
                if (client != null) {
                    client.getChat().leaveChannel(channelName);
                    if (client.getEventSocket() != null) {
                        client.getEventSocket().close();
                    }
                    client.close();
                }
            } catch (Exception e) {
                Synergy.getLogger().error("Disconnect error " + channelName + ": " + e.getMessage());
            }
        });
    	ConnectionManager.channelClients.clear();
    }

}