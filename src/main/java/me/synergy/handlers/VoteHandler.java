package me.synergy.handlers;

import java.sql.SQLException;
import java.util.UUID;

import me.synergy.brains.Synergy;
import me.synergy.modules.DataManager;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;

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

        UUID uuid = null;
		try {
			uuid = new DataManager().findUuidByName(username);
		} catch (SQLException e) {
			e.printStackTrace();
		}
        
        BreadMaker bread = Synergy.getBread(uuid);
        
        Synergy.event("votifier")
        	.setPlayerUniqueId(bread.getUniqueId())
	        .setOption("service", service)
	        .setOption("username", username)
	        .send();

        String message = LocaleBuilder.of("votifier-announcement").placeholder("player", username).placeholder("service", service).placeholder("gender", bread.getGender()).build();
        
        Synergy.event("discord-embed")
            .setPlayerUniqueId(bread.getUniqueId())
            .setOption("chat", "global")
            .setOption("color", "#55efc4")
            .setOption("author", message)
            .fireEvent();
        
        bread.setData("last-voted", System.currentTimeMillis());
    }
}