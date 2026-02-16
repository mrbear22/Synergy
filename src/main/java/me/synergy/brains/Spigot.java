package me.synergy.brains;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.commands.ChatCommand;
import me.synergy.commands.DiscordCommand;
import me.synergy.commands.DynamicCommands;
import me.synergy.commands.LanguageCommand;
import me.synergy.commands.MonobankCommand;
import me.synergy.commands.PronounCommand;
import me.synergy.commands.SynergyCommand;
import me.synergy.commands.ThemeCommand;
import me.synergy.commands.TikTokCommand;
import me.synergy.commands.TwitchCommand;
import me.synergy.commands.VoteCommand;
import me.synergy.discord.Discord;
import me.synergy.discord.RolesHandler;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.ChatHandler;
import me.synergy.handlers.LocalesHandler;
import me.synergy.handlers.PlayerSpigotHandler;
import me.synergy.handlers.ResourcePackHandler;
import me.synergy.handlers.ServerListPingHandler;
import me.synergy.handlers.VoteHandler;
import me.synergy.integrations.EssentialsAPI;
import me.synergy.integrations.PlaceholdersAPI;
import me.synergy.integrations.PlanAPI;
import me.synergy.integrations.VaultAPI;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.twitch.Twitch;
import me.synergy.utils.RepeatingTask;
import me.synergy.utils.UpdateChecker;
import me.synergy.web.MonobankHandler;
import me.synergy.web.TikTokHandler;
import me.synergy.web.WebServer;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public class Spigot extends JavaPlugin implements PluginMessageListener, SynergyListener {

    private static Spigot INSTANCE;
    private ProtocolManager PROTOCOLMANAGER;
    private static Economy econ;
    private static Permission perms;
    private static Chat chat;

    @Override
	public void onEnable() {
        INSTANCE = this;
        Synergy.platform = "spigot";

        getServer().getMessenger().registerOutgoingPluginChannel(this, "net:synergy");
        getServer().getMessenger().registerIncomingPluginChannel(this, "net:synergy", this);

        new Config().initialize();
        new DataManager().initialize();
        new Locales().initialize();
        new SynergyCommand().initialize();
        new ChatCommand().initialize();
        new VoteCommand().initialize();
        new PronounCommand().initialize();
        new LanguageCommand().initialize();
        new DiscordCommand().initialize();
        new TwitchCommand().initialize();
        new MonobankHandler().initialize();
        new MonobankCommand().initialize();
        new ChatHandler().initialize();
        new Discord().initialize();
	    new Twitch().initialize();
        new ServerListPingHandler().initialize();
        new PlayerSpigotHandler().initialize();
        new WebServer().initialize();
        new ResourcePackHandler().initialize();
        new ThemeCommand().initialize();
		new VoteHandler().initialize();
		new DynamicCommands().initialize();
        new TikTokHandler().initialize();
        new TikTokCommand().initialize();
		
		if (Synergy.isDependencyAvailable("ProtocolLib")) {
			PROTOCOLMANAGER = ProtocolLibrary.getProtocolManager();
	        new LocalesHandler().initialize();
		}
        
		if (Synergy.isDependencyAvailable("Essentials")) {
			new EssentialsAPI().initialize();
		}
        
		if (Synergy.isDependencyAvailable("Vault")) {
			new VaultAPI().initialize();
	        setupEconomy();
	        setupPermissions();
	        //setupChat();
	        new RolesHandler().initialize();
		}

		if (Synergy.isDependencyAvailable("Plan")) {
			new PlanAPI().initialize();
		}
        
		if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
	        new PlaceholdersAPI().initialize();
		}
		
        Synergy.getEventManager().registerEvents(this);
		
		new UpdateChecker("mrbear22", "Synergy").checkForUpdates();
		
        getLogger().info("Synergy is ready to be helpful for the all BreadMakers!");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!channel.equals("net:synergy")) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String token = in.readUTF();
	    String identifier = in.readUTF();
	    String stringUUID = in.readUTF();
	    
	    UUID uuid = null;
	    if (stringUUID != null && !stringUUID.isEmpty()) {
	        try {
	            uuid = UUID.fromString(stringUUID);
	        } catch (IllegalArgumentException e) { }
	    }
	    
	    String data = in.readUTF();
        if (token.equals(Synergy.getSynergyToken())) {
        	new SynergyEvent(identifier, uuid, data).fireEvent();
        	Bukkit.getServer().getPluginManager().callEvent(new me.synergy.bukkit.events.SynergyEvent(identifier, uuid, data));
        }
    }

    public void sendPluginMessage(byte[] data) {
    	Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
    	if (player != null) {
    		player.sendPluginMessage(this, "net:synergy", data);
    	} else {
        	Bukkit.getServer().sendPluginMessage(this, "net:synergy", data);
    	}
    }
    
   
    @Override
	public void onDisable() {
        new Discord().shutdown();
        new WebServer().shutdown();
        new RepeatingTask().shutdown();
		new DynamicCommands().shutdown();
        getLogger().info("Synergy has stopped it's service!");
    }
    
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
        RegisteredServiceProvider <Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
			return false;
		}
        setEconomy(rsp.getProvider());
        return (getEconomy() != null);
    }

    private boolean setupPermissions() {
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider <Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
            setPermissions(rsp.getProvider());
            return (getPermissions() != null);
        }
        return false;
    }

    @SuppressWarnings("unused")
	private boolean setupChat() {
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider <Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
            setChat(rsp.getProvider());
            return (getChat() != null);
        }
        return false;
    }

    private void setChat(Chat chat) {
		Spigot.chat = chat;
	}

	public Chat getChat() {
        return chat;
    }

	public Economy getEconomy() {
        return econ;
    }

    public static void setEconomy(Economy econ) {
        Spigot.econ = econ;
    }

    public Permission getPermissions() {
        return perms;
    }

    public static void setPermissions(Permission perms) {
        Spigot.perms = perms;
    }

    public ProtocolManager getProtocolManager() {
        return this.PROTOCOLMANAGER;
    }
	
	public static Spigot getInstance() {
		return INSTANCE;
	}

	public String getPlayerName(UUID uniqueId) {
		return uniqueId == null ? null : Bukkit.getOfflinePlayer(uniqueId) == null ? null : Bukkit.getOfflinePlayer(uniqueId).getName();
	}

	public UUID getUniqueIdFromName(String username) {
		return username == null ? null : Bukkit.getOfflinePlayer(username) == null ? null : Bukkit.getOfflinePlayer(username).getUniqueId();
	}

	public String getPlayerLanguage(UUID uniqueId) {
	    Player player = Bukkit.getPlayer(uniqueId);
	    if (player == null) return "en";
	    String language = player.locale().getLanguage();
	    return Synergy.getLocalesManager().getLanguages().contains(language) ? language : "en";
	}

	public void executeConsoleCommand(String command) {
		 Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
	}

	public Player getPlayerByUniqueId(UUID uniqueId) {
		return Bukkit.getPlayer(uniqueId);
	}

	public OfflinePlayer getOfflinePlayerByUniqueId(UUID uniqueId) {
		return Bukkit.getOfflinePlayer(uniqueId);
	}
	
	public boolean playerHasPermission(UUID uniqueId, String node) {
		return getPlayerByUniqueId(uniqueId) == null ? false : getPlayerByUniqueId(uniqueId).hasPermission(node);
	}

	@SynergyHandler
	public void synergyEvent(SynergyEvent event) {
		if (!event.getIdentifier().equals("dispatch-command")) {
			return;
		}
		dispatchCommand(event.getOption("command").getAsString());
	}
	
	public void dispatchCommand(String string) {
	    Bukkit.getScheduler().runTask(this, new Runnable() {
	        @Override
	        public void run() {
	            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), string);
	        }
	    });
	}
	
    public void startSpigotMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
            	new WebServer().monitorServer();
            }
        }.runTaskTimerAsynchronously(this, 0L, WebServer.MONITOR_INTERVAL_SECONDS * 20L);
    }

    public static void kick(UUID uniqueId, String reason) {
        BreadMaker bread = Synergy.getBread(uniqueId);
        Player player = Bukkit.getPlayer(uniqueId);
        if (player != null) {
            player.kick(Synergy.translate(reason, bread.getLanguage())
                .setPlaceholders(bread)
                .setGendered(bread.getGender())
                .getColoredComponent(bread.getTheme()));
        }
    }

	public Location getPlayerLocation(UUID uuid) {
		return Bukkit.getPlayer(uuid).getLocation();
	}

}