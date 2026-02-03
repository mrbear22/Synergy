package me.synergy.brains;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import me.synergy.anotations.SynergyHandler;
import me.synergy.commands.SynergyVelocityCommand;
import me.synergy.discord.Discord;
import me.synergy.discord.RolesHandler;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.PlayerVelocityHandler;
import me.synergy.handlers.VoteHandler;
import me.synergy.integrations.PlanAPI;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.LocalesManager;
import me.synergy.objects.BreadMaker;
import me.synergy.web.WebServer;

@Plugin(id = "synergy", name = "Synergy", version = "0.0.2-SNAPSHOT",
url = "archi.quest", description = "Basic tools and messaging plugin", authors = {"mrbear22"})
public class Velocity {

    private static ProxyServer server;
    private static Logger logger;
    public Map<String, Object> configValues;
	public Object config;
	private static Velocity INSTANCE;

    public static final String CHANNEL_NAME = "net:synergy";
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from(CHANNEL_NAME);

    @Inject
    public Velocity(ProxyServer server, Logger logger) {
        Velocity.server = server;
        Velocity.setLogger(logger);

        logger.info("Synergy is ready to be helpful for all beadmakers!");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
    	INSTANCE = this;
    	Synergy.platform = "velocity";
	    server.getChannelRegistrar().register(IDENTIFIER);
	    new Config().initialize();
	    new DataManager().initialize();
        new LocalesManager().initialize();
	    new Discord().initialize();
	    new PlayerVelocityHandler().initialize();
	    new SynergyVelocityCommand().initialize();
		new VoteHandler().initialize();
	    new WebServer().initialize();
        new RolesHandler().initialize();
    	if (Synergy.isDependencyAvailable("Plan")) {
    		new PlanAPI().initialize();
    	}
		
    }

    @Subscribe
    public void onPluginMessageFromSpigot(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(IDENTIFIER)) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String identifier = in.readUTF();
        String stringUUID = in.readUTF();
	    UUID uuid = null;
	    if (stringUUID != null && !stringUUID.isEmpty()) {
	        try {
	            uuid = UUID.fromString(stringUUID);
	        } catch (IllegalArgumentException e) { }
	    }
        String data = in.readUTF();

        new SynergyEvent(identifier, uuid, data).send();
        new SynergyEvent(identifier, uuid, data).fireEvent();
    }
    
    public void sendPluginMessage(byte[] byteArray) {
        MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.from("net:synergy");
        for (RegisteredServer server : getProxy().getAllServers()) {
            server.sendPluginMessage(channel, byteArray);
        }
    }
    
	@SynergyHandler
	public void onEvent(SynergyEvent event) {
        if (!event.getIdentifier().equals("chat")) {
            return;
        }
		getLogger().info(event.getIdentifier());
	}

    public static Logger getLogger() {
    	return logger;
    }

    public static ProxyServer getProxy() {
    	return server;
    }

	public Config getConfig() {
		return new Config();
	}

	public static Velocity getInstance() {
		return INSTANCE;
	}

	public static void setLogger(Logger logger) {
		Velocity.logger = logger;
	}

	public static void kick(UUID uniqueId, String reason) {
	    BreadMaker bread = Synergy.getBread(uniqueId);
	    Player player = getProxy().getPlayer(uniqueId).orElse(null);
	    if (player != null) {
	        player.disconnect(Synergy.translate(reason, bread.getLanguage())
	            .setPlaceholders(bread)
	            .setGendered(bread.getGender())
	            .getColoredComponent(bread.getTheme()));
	    }
	}

}
