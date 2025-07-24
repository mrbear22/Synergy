package me.synergy.brains;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import me.synergy.commands.SynergyProxyCommand;
import me.synergy.discord.Discord;
import me.synergy.discord.RolesHandler;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.PlayerBungeeHandler;
import me.synergy.handlers.VoteHandler;
import me.synergy.integrations.PlanAPI;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.LocalesManager;
import me.synergy.objects.BreadMaker;
import me.synergy.web.WebServer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class Bungee extends Plugin implements Listener {

	private static Bungee INSTANCE;
	
	@Override
	public void onEnable() {
		
    	INSTANCE = this;
    	Synergy.platform = "bungee";
    	
	    new Config().initialize();
	    new DataManager().initialize();
        new LocalesManager().initialize();
	    new Discord().initialize();
	    new PlayerBungeeHandler().initialize();
	    new SynergyProxyCommand().initialize();
	    new WebServer().initialize();
        new RolesHandler().initialize();
        new VoteHandler().initialize();
        
    	if (Synergy.isDependencyAvailable("Plan")) {
    		new PlanAPI().initialize();
    	}
        
	    getProxy().getPluginManager().registerListener(this, this);
	    
	    getProxy().registerChannel("net:synergy");
	    
		getLogger().info("Synergy is ready to be helpful for all beadmakers!");
	}

	public void onDisable() {
		getLogger().info("Synergy has stopped it's service!");

	}
	
	public Config getConfig() {
		return new Config();
	}

	public static Bungee getInstance() {
		return INSTANCE;
	}

	@EventHandler
	public void onPluginMessage(PluginMessageEvent event) {
	    if (!event.getTag().equals("net:synergy")) {
	        return;
	    }
	    
	    ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
	    String identifier = in.readUTF();
	    String stringUUID = in.readUTF();
	    
	    UUID uuid = null;
	    if (stringUUID != null && !stringUUID.isEmpty()) {
	        try {
	            uuid = UUID.fromString(stringUUID);
	        } catch (IllegalArgumentException e) {
	        }
	    }
	    
	    String data = in.readUTF();
	    
	    new SynergyEvent(identifier, uuid, data).send();
	    new SynergyEvent(identifier, uuid, data).fireEvent();
	}

	public String getPlayerName(UUID uniqueId) {
		return getProxy().getPlayer(uniqueId).getDisplayName();
	}
	
    public void startBungeeMonitor() {
    	ProxyServer.getInstance().getScheduler().schedule(this, new Runnable() {
           @Override
           public void run() {
        	   new WebServer().monitorServer();
           }
	   }, 0L, WebServer.MONITOR_INTERVAL_SECONDS, TimeUnit.SECONDS);
   }

    @SuppressWarnings("deprecation")
    public static void kick(UUID uniqueId, String reason) {
        BreadMaker bread = Synergy.getBread(uniqueId);
        ProxiedPlayer player = getInstance().getProxy().getPlayer(uniqueId);
        if (player != null) {
            player.disconnect(Synergy.translate(reason, bread.getLanguage()).getColored(bread.getTheme()));
        }
    }

	
}