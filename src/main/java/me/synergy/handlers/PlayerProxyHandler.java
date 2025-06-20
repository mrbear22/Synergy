package me.synergy.handlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Translation;
import net.dv8tion.jda.api.entities.User;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PlayerProxyHandler implements Listener {

    private static Set<UUID> kickedPlayers = new HashSet<>();
    
	public void initialize() {
		Bungee.getInstance().getProxy().getPluginManager().registerListener(Bungee.getInstance(), this);
        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	}
	
	@EventHandler
	public void onServerConect(ServerConnectEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
	    bread.setData("name", event.getPlayer().getName());
	    ProxiedPlayer player = event.getPlayer();
		
	    if (!Synergy.getConfig().getBoolean("discord.enabled")) {
	    	return;
	    }
	   
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
        	if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
    	        return;
        	}
        }
	}
	
	@EventHandler
	public void onPlayerLogin(PostLoginEvent event) {
	    BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
	    bread.setData("name", event.getPlayer().getName());
	    ProxiedPlayer player = event.getPlayer();
		
	    if (!Synergy.getConfig().getBoolean("discord.enabled")) {
	    	return;
	    }
	   
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
        	if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
    	        return;
        	}
        }

        if (Synergy.getConfig().getBoolean("discord.kick-player.if-missing.enabled")) {
        	if (Discord.isMissing(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-missing.message"));
    	        return;
        	}
        }

        if (Synergy.getConfig().getBoolean("discord.kick-player.if-muted.enabled")) {
        	if (Discord.isMuted(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-muted.message"));
    	        return;
        	}
        }
        
        if (bread.getData("confirm-discord").isSet()) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
            	User user = Synergy.getDiscord().getUserById(bread.getData("confirm-discord").getAsString());
            	if (user != null) {
            		bread.sendMessage(Translation.translate("<lang>synergy-link-discord-confirmation</lang>", bread.getLanguage()).replace("%ACCOUNT%", user.getEffectiveName()));
            	}
                scheduler.shutdown();
            }, 2, TimeUnit.SECONDS);
        	
        }
        
	    if (Synergy.getConfig().getBoolean("discord.player-join-leave-messages")) {
		    Synergy.createSynergyEvent("discord-embed")
		        .setPlayerUniqueId(player.getUniqueId())
		        .setOption("chat", "global")
		        .setOption("color", "#81ecec")
		        .setOption("author", Synergy.translate("<lang>synergy-player-join-message<arg>" + player.getName() + "</arg></lang>", Translation.getDefaultLanguage())
		            .setEndings(bread.getPronoun())
		            .getStripped())
		        .fireEvent();
	    }
	}



    
    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
    	ProxiedPlayer player = event.getPlayer();
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
    	
        if (kickedPlayers.remove(event.getPlayer().getUniqueId())) {
        	return;
        }
        if (!Synergy.getConfig().getBoolean("discord.enabled") || !Synergy.getConfig().getBoolean("discord.player-join-leave-messages")) {
            return;
        }
    	Synergy.createSynergyEvent("discord-embed").setPlayerUniqueId(player.getUniqueId())
    	.setOption("chat", "global")
    	.setOption("color", "#fab1a0")
    	.setOption("author", Synergy.translate("<lang>synergy-player-quit-message<arg>"+player.getName()+"</arg></lang>", Translation.getDefaultLanguage())
    			.setEndings(bread.getPronoun())
    			.getStripped()).fireEvent();
    }
	
}
