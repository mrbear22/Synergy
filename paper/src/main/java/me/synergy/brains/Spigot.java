package me.synergy.brains;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.ServerListPingEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.google.common.collect.Iterables;

import me.clip.placeholderapi.PlaceholderAPI;
import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.bukkit.events.PlayerMuteStatusChangeEvent;
import me.synergy.commands.Sender;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.LocalesHandler;
import me.synergy.handlers.ChatHandler;
import me.synergy.handlers.Motd;
import me.synergy.handlers.PlayerSessions;
import me.synergy.handlers.ResourcePacks;
import me.synergy.events.SynergyVotifierEvent;
import me.synergy.integrations.AuthmeAPI;
import me.synergy.integrations.PlaceholdersAPI;
import me.synergy.modules.Messaging;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Spigot extends JavaPlugin implements Listener, PluginMessageListener, SynergyListener, Platform {

    private static Spigot INSTANCE;
    private LocalesHandler localesHandler;

    @Override
	public void onEnable() {
        INSTANCE = this;
        getServer().getMessenger().registerOutgoingPluginChannel(this, "net:synergy");
        getServer().getMessenger().registerIncomingPluginChannel(this, "net:synergy", this);

        Synergy.getEventManager().registerEvents(this);
        Synergy.start(this, new PlaceholdersAPI(), new AuthmeAPI());
        getServer().getPluginManager().registerEvents(this, this);
        hookPacketEvents();
        getServer().getScheduler().runTask(this, this::hookPacketEvents);
    }

    private void hookPacketEvents() {
        if (localesHandler == null && getServer().getPluginManager().isPluginEnabled("packetevents")) (localesHandler = new LocalesHandler()).initialize();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (channel.equals("net:synergy")) Messaging.receive(message, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!ChatHandler.isEnabled()) return;
        Set<UUID> viewers = new HashSet<>();
        event.viewers().forEach(a -> { if (a instanceof Player r) viewers.add(r.getUniqueId()); });
        Player p = event.getPlayer();
        String format = ChatHandler.handle(p.getUniqueId(), p.getName(), LegacyComponentSerializer.legacyAmpersand().serialize(event.message()), viewers, true);
        if (format == null) {
            event.setCancelled(true);
            return;
        }
        event.viewers().removeIf(a -> a instanceof Player r && !viewers.contains(r.getUniqueId()));
        event.renderer((s, n, m, v) -> v instanceof Player r ? ChatHandler.render(format, r.getUniqueId()) : m);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (ChatHandler.blockCommand(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage())) event.setCancelled(true);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        PlayerSessions.preload(event.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        PlayerSessions.joinAsync(player.getUniqueId(), player.getName(), String.valueOf(player.getAddress()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        PlayerSessions.quitAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        Component reason = PlayerSessions.kicked(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.reason());
        if (reason != null) event.reason(reason);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        ResourcePacks.status(event.getPlayer().getUniqueId(), event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        Component motd = Motd.message();
        if (motd == null) return;
        event.motd(motd);
        event.setMaxPlayers(Motd.maxPlayers());
    }

    @Override
    public void onDisable() {
        Synergy.stop();
        if (localesHandler != null) localesHandler.shutdown();
    }

	@SynergyHandler("dispatch-command")
	public void onDispatch(SynergyEvent event) {
		console(event.getOption("command").getAsString());
	}

	@SynergyHandler
	public void synergyEvent(SynergyEvent event) {
		Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().callEvent(new me.synergy.bukkit.events.SynergyEvent(event)));
	}

	@Override
	public void register(me.synergy.commands.Command command) {
		for (String label : command.labels()) {
			PluginCommand pc = getCommand(label);
			if (pc == null) {
				try {
					Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
					constructor.setAccessible(true);
					pc = constructor.newInstance(label, this);
					getServer().getCommandMap().register(getPluginMeta().getName(), pc);
				} catch (ReflectiveOperationException e) {
					warning("Failed to register /" + label);
					continue;
				}
			}
			pc.setExecutor((s, c, l, args) -> {
				Sender sender = sender(s);
				command.run(sender, l, args);
				return true;
			});
			pc.setTabCompleter((s, c, l, args) -> command.suggest(sender(s), l, args));
		}
	}

	@Override
	public void unregister(me.synergy.commands.Command command) {
		command.labels().forEach(label -> {
			var registered = getServer().getCommandMap().getCommand(label);
			if (registered != null) registered.unregister(getServer().getCommandMap());
		});
	}

	@Override
	public void dispatch(UUID uuid, String command) {
		Bukkit.getScheduler().runTask(this, () -> {
			CommandSender sender = uuid == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(uuid);
			if (sender != null) Bukkit.dispatchCommand(sender, command);
		});
	}

	@Override
	public List<String> complete(UUID uuid, String input) {
		CommandSender sender = uuid == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(uuid);
		List<String> result = sender == null ? null : getServer().getCommandMap().tabComplete(sender, "/" + input);
		return result == null ? List.of() : result.stream().map(s -> s.startsWith("/") ? s.substring(1) : s).toList();
	}

	private static Sender sender(CommandSender sender) {
		return sender instanceof Player player ? new Sender(player.getUniqueId(), player.getName()) : Sender.CONSOLE;
	}

	@Override public String name() { return "spigot"; }
	@Override public boolean isProxy() { return false; }
	@Override public String version() { return getPluginMeta().getVersion(); }

	@Override public void info(String message) { getLogger().info(message); }
	@Override public void warning(String message) { getLogger().warning(message); }
	@Override public void error(String message) { getLogger().severe(message); }


	@Override
	public void console(String command) {
	    Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
	}

	@Override public boolean hasDependency(String id) { return getServer().getPluginManager().isPluginEnabled(id); }

    @Override
    public boolean send(String server, byte[] data) {
        Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
        if (player == null) return false;
        player.sendPluginMessage(this, "net:synergy", data);
        return true;
    }

	@Override public Collection<UUID> online() { return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList(); }
	@Override public boolean isOnline(UUID uuid) { return Bukkit.getPlayer(uuid) != null; }

	@Override
	public boolean hasPermission(UUID uuid, String node) {
		Player player = Bukkit.getPlayer(uuid);
		return player != null && player.hasPermission(node);
	}

	@Override
	public String locale(UUID uuid) {
	    Player player = Bukkit.getPlayer(uuid);
	    return player == null ? null : player.locale().getLanguage();
	}

	@Override
	public Audience audience(UUID uuid) {
		Player player = Bukkit.getPlayer(uuid);
		return player == null ? Audience.empty() : player;
	}

	@Override
	public void kick(UUID uuid, Component reason) {
		Bukkit.getScheduler().runTask(this, () -> {
			Player player = Bukkit.getPlayer(uuid);
			if (player != null) player.kick(reason);
		});
	}

	@Override
	public Map<String, List<String>> playersByServer() {
		return Map.of(Synergy.getServerName(), Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
	}

	@Override
	public Optional<Position> position(UUID uuid) {
		Player player = Bukkit.getPlayer(uuid);
		if (player == null) return Optional.empty();
		Location l = player.getLocation();
		return Optional.of(new Position(l.getWorld().getName(), l.getX(), l.getY(), l.getZ()));
	}

	@Override
	public String placeholders(UUID uuid, String text) {
		if (!PlaceholdersAPI.enabled()) return Platform.super.placeholders(uuid, text);
		return PlaceholderAPI.setPlaceholders(uuid == null ? null : Bukkit.getOfflinePlayer(uuid), text);
	}

	@Override
	public boolean isAuthenticated(UUID uuid) {
		return AuthmeAPI.isAuthenticated(uuid);
	}

	@Override
	public void onPunishment(me.synergy.objects.Punishment punishment, String action, String origin) {
		Bukkit.getPluginManager().callEvent(new me.synergy.bukkit.events.PunishmentEvent(punishment,
			me.synergy.bukkit.events.PunishmentEvent.Action.valueOf(action.toUpperCase()), origin));
	}

	@Override
	public void onMuteChange(UUID uuid, boolean muted, long expires, String reason, String staff) {
		fireMuteStatusChangeEvent(uuid, muted, expires, reason, staff);
	}

	@Override
	public void onVote(UUID uuid, String service, String username) {
		Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().callEvent(new SynergyVotifierEvent(uuid, service, username)));
	}

	@Override
	@SuppressWarnings("deprecation")
	public Key sound(String name) {
		try {
			return org.bukkit.Sound.valueOf(name.toUpperCase()).key();
		} catch (IllegalArgumentException e) {
			return Key.key(name);
		}
	}

	public static Spigot getInstance() {
		return INSTANCE;
	}

	public static boolean fireMuteStatusChangeEvent(UUID uuid, boolean muted, long expires, String reason, String staff) {
	    if (!Bukkit.isPrimaryThread()) {
	        Bukkit.getScheduler().runTask(getInstance(), () ->
	            fireMuteStatusChangeEvent(uuid, muted, expires, reason, staff));
	        return true;
	    }
	    var event = new PlayerMuteStatusChangeEvent(uuid, muted, expires, reason, staff);
	    Bukkit.getPluginManager().callEvent(event);
	    return !event.isCancelled();
	}

}
