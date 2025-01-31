package me.synergy.handlers;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Translation;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PlayerProxyHandler implements Listener {

	public void initialize() {
		Bungee.getInstance().getProxy().getPluginManager().registerListener(Bungee.getInstance(), this);
        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	}
	
	@EventHandler
	public void onServerConect(ServerConnectEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
	}
	
    @EventHandler
    public void onPlayerLogin(PostLoginEvent event) {
        if (!Synergy.getConfig().getBoolean("discord.enabled") || !Synergy.getConfig().getBoolean("discord.player-join-leave-messages")) {
            return;
        }
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.setData("name", event.getPlayer().getName());
        
    	ProxiedPlayer player = event.getPlayer();
    	Synergy.createSynergyEvent("discord-embed")
    	.setPlayerUniqueId(player.getUniqueId())
    	.setOption("chat", "global")
    	.setOption("color", "#81ecec")
    	.setOption("author", Synergy.translate("<lang>synergy-player-join-message<arg>"+player.getName()+"</arg></lang>", Translation.getDefaultLanguage())
    			.setEndings(bread.getPronoun())
    			.getStripped()).fireEvent();
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        if (!Synergy.getConfig().getBoolean("discord.enabled") || !Synergy.getConfig().getBoolean("discord.player-join-leave-messages")) {
            return;
        }
    	ProxiedPlayer player = event.getPlayer();
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
		
    	Synergy.createSynergyEvent("discord-embed").setPlayerUniqueId(player.getUniqueId())
    	.setOption("chat", "global")
    	.setOption("color", "#fab1a0")
    	.setOption("author", Synergy.translate("<lang>synergy-player-quit-message<arg>"+player.getName()+"</arg></lang>", Translation.getDefaultLanguage())
    			.setEndings(bread.getPronoun())
    			.getStripped()).fireEvent();
    }
	
}
