package me.synergy.handlers;

import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Translation;

public class VoteHandler {
    
    public void initialize() {
        if (Synergy.isRunningBungee()) {
        	new VoteBungeeHandler().initialize();
        } else if (Synergy.isRunningSpigot()) {
            new VoteSpigotHandler().initialize();
        }
    }
	
    public static void processVote(String service, String username) {
    	
        Synergy.getLogger().info("Player " + username + " voted on " + service);

        BreadMaker bread = Synergy.getBread(Synergy.getOfflineUniqueId(username));
        
        Synergy.event("votifier")
        	.setPlayerUniqueId(bread.getUniqueId())
	        .setOption("service", service)
	        .setOption("username", username)
	        .send();

        String message = Synergy.getConfig().getString("votifier.announcement").replace("%PLAYER%", username).replace("%SERVICE%", service);
        
        Synergy.event("discord-embed")
            .setPlayerUniqueId(bread.getUniqueId())
            .setOption("chat", "global")
            .setOption("color", "#55efc4")
            .setOption("author", Synergy.translate(message, Translation.getDefaultLanguage()).setGendered(bread.getGender()).getStripped())
            .fireEvent();
        
        bread.setData("last-voted", System.currentTimeMillis());
    }
}