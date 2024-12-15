package me.synergy.integrations;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import net.milkbowl.vault.permission.Permission;

public class VaultAPI implements SynergyListener {

	public void initialize() {
        try {
	        Synergy.getEventManager().registerEvents(this);
	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	    } catch (Exception exception) {
	        Synergy.getLogger().error(String.valueOf(getClass().getSimpleName()) + " module failed to initialize: " + exception.getMessage());
	    	exception.printStackTrace();
	    }
	}
	
    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
        if (event.getIdentifier().equals("remove-player-group")) {
            if (Synergy.isDependencyAvailable("Vault") && Synergy.getConfig().getBoolean("discord-roles-sync.use-vault")) {
            	Synergy.getSpigot().getPermissions().playerRemoveGroup(Bukkit.getPlayer(event.getPlayerUniqueId()), event.getOption("group").getAsString());
            } else {
            	Synergy.dispatchCommand(Synergy.getConfig().getString("discord-roles-sync.custom-command-remove").replace("%PLAYER%", event.getBread().getName()).replace("%GROUP%", event.getOption("group").getAsString()));
            }
        }

        if (event.getIdentifier().equals("set-player-group")) {
            if (Synergy.isDependencyAvailable("Vault") && Synergy.getConfig().getBoolean("discord-roles-sync.use-vault")) {
                Synergy.getSpigot().getPermissions().playerAddGroup(Bukkit.getPlayer(event.getPlayerUniqueId()), event.getOption("group").getAsString());
            } else {
                Synergy.dispatchCommand(Synergy.getConfig().getString("discord-roles-sync.custom-command-add").replace("%PLAYER%", event.getBread().getName()).replace("%GROUP%", event.getOption("group").getAsString()));
            }
        }
    }
	
    public List<OfflinePlayer> getPlayersWithPermission(String permissionNode) {
        List<OfflinePlayer> playersWithPermission = new ArrayList<>();
        if (Synergy.getSpigot().getPermissions() != null) {
            for (OfflinePlayer player : Bukkit.getServer().getOfflinePlayers()) {
                if (Synergy.getSpigot().getPermissions().playerHas(null, player, permissionNode)) {
                    playersWithPermission.add(player);
                }
            }
        }
        return playersWithPermission;
    }

    public OfflinePlayer getPlayerWithExactPermission(String permissionNode) {
    	Permission permission = Synergy.getSpigot().getPermissions();
        if (permission != null) {
            for (OfflinePlayer player : Bukkit.getServer().getOfflinePlayers()) {
                if (permission.playerHas(null, player, permissionNode) || permission.playerHas(null, player, permissionNode + ".*")) {
                    if (permission.playerHas(null, player, permissionNode) && !permission.playerHas(null, player, permissionNode + ".*")) {
                        return player;
                    }
                }
            }
        }
        return null;
    }

}
