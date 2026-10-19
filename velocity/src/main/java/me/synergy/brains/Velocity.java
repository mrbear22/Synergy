package me.synergy.brains;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import me.synergy.commands.Command;
import me.synergy.commands.Sender;
import me.synergy.handlers.PlayerVelocityHandler;
import me.synergy.modules.Messaging;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public class Velocity implements Platform {

    private static ProxyServer server;
    private static Logger logger;
	private static Velocity INSTANCE;

    public static final String CHANNEL_NAME = "net:synergy";
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from(CHANNEL_NAME);

    @Inject
    public Velocity(ProxyServer server, Logger logger) {
        Velocity.server = server;
        Velocity.logger = logger;
        System.getProperties().putIfAbsent("velocity.max-known-packs", "1024");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
    	INSTANCE = this;
	    server.getChannelRegistrar().register(IDENTIFIER);
	    Synergy.start(this, new PlayerVelocityHandler());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        Synergy.stop();
    }

    @Subscribe
    public void onPluginMessageFromSpigot(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(IDENTIFIER)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (event.getSource() instanceof ServerConnection connection) Messaging.receive(event.getData(), connection.getServerInfo().getName());
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer().ifPresent(c -> server.getScheduler().buildTask(this, () -> Messaging.flush(c.getServerInfo().getName())).delay(1, TimeUnit.SECONDS).schedule());
    }

    public static Logger getLogger() {
    	return logger;
    }

    public static ProxyServer getProxy() {
    	return server;
    }

	public static Velocity getInstance() {
		return INSTANCE;
	}

	@Override public String name() { return "velocity"; }
	@Override public boolean isProxy() { return true; }

	@Override
	public String version() {
		return server.getPluginManager().getPlugin("synergy").flatMap(p -> p.getDescription().getVersion()).orElse("unknown");
	}

	@Override public void info(String message) { logger.info(message); }
	@Override public void warning(String message) { logger.warn(message); }
	@Override public void error(String message) { logger.error(message); }

	@Override public void console(String command) { server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command); }
	@Override public boolean hasDependency(String id) { return server.getPluginManager().getPlugin(id.toLowerCase()).isPresent(); }

    @Override
    public boolean send(String name, byte[] data) {
        return server.getServer(name).map(s -> s.sendPluginMessage(IDENTIFIER, data)).orElse(false);
    }

    @Override
    public String serverOf(UUID uuid) {
        return server.getPlayer(uuid).flatMap(Player::getCurrentServer).map(c -> c.getServerInfo().getName()).orElse(null);
    }

	@Override public Collection<UUID> online() { return server.getAllPlayers().stream().map(Player::getUniqueId).toList(); }
	@Override public boolean isOnline(UUID uuid) { return server.getPlayer(uuid).isPresent(); }
	@Override public boolean hasPermission(UUID uuid, String node) { return server.getPlayer(uuid).map(p -> p.hasPermission(node)).orElse(false); }
	@Override public String locale(UUID uuid) { return null; }
	@Override public Audience audience(UUID uuid) { return server.getPlayer(uuid).<Audience>map(p -> p).orElse(Audience.empty()); }
	@Override public void kick(UUID uuid, Component reason) { server.getPlayer(uuid).ifPresent(p -> p.disconnect(reason)); }

	@Override
	public Map<String, List<String>> playersByServer() {
		Map<String, List<String>> result = new LinkedHashMap<>();
		server.getAllServers().forEach(s -> result.put(s.getServerInfo().getName(),
			s.getPlayersConnected().stream().map(Player::getUsername).toList()));
		return result;
	}

	@Override
	public String label(String label) {
		return "v" + label;
	}

	@Override
	public void register(Command command) {
		for (String label : command.labels()) server.getCommandManager().register(server.getCommandManager().metaBuilder(label(label)).plugin(this).build(), new SimpleCommand() {
			@Override
			public void execute(Invocation invocation) {
				command.run(sender(invocation.source()), label, invocation.arguments());
			}

			@Override
			public List<String> suggest(Invocation invocation) {
				return command.suggest(sender(invocation.source()), label, invocation.arguments().length == 0 ? new String[] {""} : invocation.arguments());
			}
		});
	}

	@Override
	public void unregister(Command command) {
		command.labels().forEach(label -> server.getCommandManager().unregister(label(label)));
	}

	private static Sender sender(CommandSource source) {
		return source instanceof Player player ? new Sender(player.getUniqueId(), player.getUsername()) : Sender.CONSOLE;
	}
}
