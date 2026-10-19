package me.synergy.brains;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import me.synergy.commands.Command;
import me.synergy.commands.Sender;
import me.synergy.handlers.PlayerBungeeHandler;
import me.synergy.modules.Messaging;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;

public class Bungee extends Plugin implements Listener, Platform {

	private static Bungee INSTANCE;

	@Override
	public void onEnable() {
    	INSTANCE = this;
	    getProxy().getPluginManager().registerListener(this, this);
	    getProxy().registerChannel("net:synergy");
	    Synergy.start(this, new PlayerBungeeHandler());
	}

	@Override
	public void onDisable() {
		Synergy.stop();
	}

	public static Bungee getInstance() {
		return INSTANCE;
	}

	@EventHandler
	public void onPluginMessage(PluginMessageEvent event) {
		if (!event.getTag().equals("net:synergy")) return;
		event.setCancelled(true);
		if (event.getSender() instanceof Server server) Messaging.receive(event.getData(), server.getInfo().getName());
	}

	@EventHandler
	public void onServerConnected(ServerConnectedEvent event) {
		String name = event.getServer().getInfo().getName();
		getProxy().getScheduler().schedule(this, () -> Messaging.flush(name), 1, TimeUnit.SECONDS);
	}

	@Override public String name() { return "bungee"; }
	@Override public boolean isProxy() { return true; }
	@Override public String version() { return getDescription().getVersion(); }

	@Override public void info(String message) { getLogger().info(message); }
	@Override public void warning(String message) { getLogger().warning(message); }
	@Override public void error(String message) { getLogger().severe(message); }

	@Override public void console(String command) { getProxy().getPluginManager().dispatchCommand(getProxy().getConsole(), command); }
	@Override public boolean hasDependency(String id) { return getProxy().getPluginManager().getPlugin(id) != null; }

	@Override
	public boolean send(String name, byte[] data) {
		ServerInfo info = getProxy().getServerInfo(name);
		return info != null && info.sendData("net:synergy", data, false);
	}

	@Override
	public String serverOf(UUID uuid) {
		ProxiedPlayer player = getProxy().getPlayer(uuid);
		return player == null || player.getServer() == null ? null : player.getServer().getInfo().getName();
	}

	@Override public Collection<UUID> online() { return getProxy().getPlayers().stream().map(ProxiedPlayer::getUniqueId).toList(); }
	@Override public boolean isOnline(UUID uuid) { return getProxy().getPlayer(uuid) != null; }

	@Override
	public boolean hasPermission(UUID uuid, String node) {
		ProxiedPlayer player = getProxy().getPlayer(uuid);
		return player != null && player.hasPermission(node);
	}

	@Override public String locale(UUID uuid) { return null; }
	@Override
	public Audience audience(UUID uuid) {
		ProxiedPlayer player = getProxy().getPlayer(uuid);
		return player == null ? Audience.empty() : new Audience() {
			@Override
			public void sendMessage(Component message) {
				player.sendMessage(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(message)));
			}
		};
	}

	@Override
	public void kick(UUID uuid, Component reason) {
		ProxiedPlayer player = getProxy().getPlayer(uuid);
		if (player != null) player.disconnect(TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(reason)));
	}

	@Override
	public Map<String, List<String>> playersByServer() {
		Map<String, List<String>> result = new LinkedHashMap<>();
		getProxy().getServers().forEach((name, server) ->
			result.put(name, server.getPlayers().stream().map(ProxiedPlayer::getDisplayName).toList()));
		return result;
	}

	private final Map<Command, List<net.md_5.bungee.api.plugin.Command>> commands = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public String label(String label) {
		return "b" + label;
	}

	@Override
	public void register(Command command) {
		List<net.md_5.bungee.api.plugin.Command> registered = command.labels().stream().<net.md_5.bungee.api.plugin.Command>map(label -> new BungeeCommand(command, label)).toList();
		registered.forEach(c -> getProxy().getPluginManager().registerCommand(this, c));
		commands.put(command, registered);
	}

	@Override
	public void unregister(Command command) {
		List<net.md_5.bungee.api.plugin.Command> registered = commands.remove(command);
		if (registered != null) registered.forEach(getProxy().getPluginManager()::unregisterCommand);
	}

	private static class BungeeCommand extends net.md_5.bungee.api.plugin.Command implements TabExecutor {

		private final Command command;
		private final String label;

		BungeeCommand(Command command, String label) {
			super(INSTANCE.label(label));
			this.command = command;
			this.label = label;
		}

		@Override
		public void execute(CommandSender source, String[] args) {
			command.run(sender(source), label, args);
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender source, String[] args) {
			return command.suggest(sender(source), label, args);
		}

		private static Sender sender(CommandSender source) {
			return source instanceof ProxiedPlayer player ? new Sender(player.getUniqueId(), player.getName()) : Sender.CONSOLE;
		}
	}
}
