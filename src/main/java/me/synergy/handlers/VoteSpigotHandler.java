package me.synergy.handlers;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

public class VoteSpigotHandler {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("votifier.enabled")) {
            return;
        }
        
        Synergy.getEventManager().registerEvents(new SynergyVoteListener());
        
        if (Synergy.isDependencyAvailable("Votifier")) {
            Bukkit.getPluginManager().registerEvents(new BukkitVoteListener(), Synergy.getSpigot());
            Synergy.getLogger().info("Bukkit VoteHandler module has been initialized in standalone mode!");
        } else {
            Bukkit.getMessenger().registerIncomingPluginChannel(
                Synergy.getSpigot(), 
                "nuvotifier:votes", 
                new NuvotifierPluginMessageListener()
            );
            Synergy.getLogger().info("Bukkit VoteHandler module has been initialized in network mode!");
        }
    }
    
    public class BukkitVoteListener implements Listener {
        
        @EventHandler(priority = EventPriority.NORMAL)
        public void onVotifierEvent(VotifierEvent event) {
            Vote vote = event.getVote();
            String service = vote.getServiceName();
            String username = vote.getUsername();
            VoteHandler.processVote(service, username);
        }
    }
    
    public class NuvotifierPluginMessageListener implements PluginMessageListener {
        
        @Override
        public void onPluginMessageReceived(String channel, Player player, byte[] message) {
            if (!channel.equals("nuvotifier:votes")) {
                return;
            }
            
            try {
                String messageStr = new String(message, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(messageStr).getAsJsonObject();
                
                String username = json.get("username").getAsString();
                String service = json.get("serviceName").getAsString();
                
                BreadMaker bread = Synergy.getBread(Synergy.getOfflineUniqueId(username));
                
                Synergy.event("votifier")
	            	.setPlayerUniqueId(bread.getUniqueId())
	    	        .setOption("service", service)
	    	        .setOption("username", username)
	    	        .fireEvent();
	                
            } catch (Exception e) {
                Synergy.getLogger().error("Error processing NuVotifier plugin message: " + e.getMessage());
            }
        }
    }
    
    public class SynergyVoteListener implements SynergyListener {
        
        @SynergyHandler
        public void onSynergyPluginMessage(SynergyEvent event) {
            if (!event.getIdentifier().equals("votifier")) {
                return;
            }
            
            String service = event.getOption("service").getAsString();
            String username = event.getOption("username").getAsString();

            Synergy.getLogger().info("Player " + username + " voted on " + service);
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().equals(username)) {
                    player.sendMessage(LocaleBuilder.of("votifier-message")
                    		.placeholder("player", username)
                    		.placeholder("service", service)
                    		.component()
                    );
                } else {
                    player.sendMessage(LocaleBuilder.of("votifier-announcement")
                    		.placeholder("player", username)
                    		.placeholder("service", service)
                    		.component()
                    );
                }
            }
            
            for (String command : Synergy.getConfig().getStringList("votifier.rewards")) {
                Synergy.dispatchCommand(command.replace("%PLAYER%", username));
            }
        }
    }
}