package me.synergy.twitch;

import java.util.Map.Entry;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;

public class Twitch implements SynergyListener {

    public void initialize() {
        try {
            if (!Synergy.getConfig().getBoolean("twitch.enabled")) return;
            Synergy.getEventManager().registerEvents(this);
            
            Locales.addDefault("command_description_twitch", "en", "Twitch integration");
            Locales.addDefault("command_usage_twitch", "en", new String[] {
    		    "<danger>Usage: /twitch <action> [arguments]",
    		    "",
    		    "<secondary>Actions:",
    		    "<primary>  link <channel> <token> <secondary>- Link your Twitch account",
    		    "<primary>  unlink                 <secondary>- Unlink your Twitch account", 
    		    "<primary>  createreward <reward>  <secondary>- Create channel point reward",
    		    "<primary>  removereward <reward>  <secondary>- Remove channel point reward",
    		    "<primary>  testreward <reward> <input> <secondary>- Test reward redemption",
    		    "",
    		    "<secondary>Arguments:",
    		    "<primary>  channel <secondary>- Your Twitch channel name",
    		    "<primary>  token   <secondary>- Your Twitch access token",
    		    "<primary>  reward  <secondary>- Reward name from config",
    		    "<primary>  input   <secondary>- Test input for reward",
    		    "",
    		    "<secondary>Examples:",
    		    "<primary>  /twitch link streamer123 your_token",
    		    "<primary>  /twitch createreward subscribe",
    		    "<primary>  /twitch testreward follow test_input",
    		    "",
    		    "<secondary>Obtain the access token here: <click:open_url:'https://twitchtokengenerator.com/quick/f7tCmJvs3S'><hover:show_text:Click to open><secondary><u>https://twitchtokengenerator.com/quick/f7tCmJvs3S</u></click>",
    		    "<danger>Keep your API token secure!"
    		});
            
            Locales.addDefault("twitch-invalid-credentials", "en", "<danger>Invalid Twitch credentials!");
            Locales.addDefault("twitch-connection-failed", "en", "<danger>Failed to connect to Twitch!");
            Locales.addDefault("twitch-failed-get-user-id", "en", "<danger>Failed to get user ID for EventSub: ");
            Locales.addDefault("twitch-eventsub-subscription-created", "en", "<success>EventSub subscription created for rewards on channel: ");
            Locales.addDefault("twitch-failed-create-eventsub", "en", "<danger>Failed to create EventSub subscription for rewards: ");
            Locales.addDefault("you-have-no-linked-twitch-accounts", "en", "<danger>No linked Twitch accounts! Link: <secondary><u>/twitch link <channel> <token></u>");
            Locales.addDefault("link-twitch-already-linked", "en", "<primary>Account already linked to <secondary>%ARGUMENT%<primary>! Unlink: <secondary><u>/twitch unlink</u>");
            
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
                        	            
                        	            Synergy.event("send-twitch-message")
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