package me.synergy.commands;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.synergy.brains.Synergy;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Translation;
import me.synergy.web.TikTokHandler;

public class TikTokCommand implements CommandExecutor, TabCompleter, Listener {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("tiktok.enabled")) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getSpigot().getCommand("tiktok").setExecutor(this);
        Synergy.getSpigot().getCommand("tiktok").setTabCompleter(this);
        
        Locales.addDefault("command_description_tiktok", "en", "Tiktok integration");
        Locales.addDefault("command_usage_tiktok", "en", new String[] {
		    "<danger>Usage: /tiktok <action> [tiktok_account]",
		    "",
		    "<secondary>Actions:",
		    "<primary>  link    <secondary>- Link Minecraft to Tiktok",
		    "<primary>  unlink  <secondary>- Unlink Tiktok account", 
		    "",
		    "<secondary>Arguments:",
		    "<primary>  tiktok_account <secondary>- Your Tiktok username",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /tiktok link @usertag",
		    "<primary>  /tiktok unlink",
		    ""
		});
        
        Locales.addDefault("tiktok_link_usage", "en", "<danger>Usage: /tiktok link <@usertag>");
        Locales.addDefault("tiktok_successfully_linked", "en", "<success>tiktok account linked!");
        Locales.addDefault("tiktok_already_linked", "en", "<danger>tiktok account already linked!");
        Locales.addDefault("tiktok_successfully_unlinked", "en", "<success>tiktok account unlinked!");
        Locales.addDefault("tiktok_not_linked", "en", "<danger>No linked tiktok account!");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        
        if (bread.getData("tiktok").isSet()) {
            String username = bread.getData("tiktok").getAsString();
            TikTokHandler.connect(uuid, username);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        TikTokHandler.disconnect(event.getPlayer().getUniqueId());
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("link", "unlink");
        }
        if (args[0].equalsIgnoreCase("link") && args.length == 2) {
            return List.of("<username>");
        }
        return List.of();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("<lang>command-not-player</lang>");
            return false;
        }
        
        Player player = (Player) sender;
        BreadMaker bread = Synergy.getBread(player.getUniqueId());
        
        if (!player.hasPermission("synergy.tiktok")) {
            sender.sendMessage("<lang>no-permission</lang>");
            return false;
        }
        
        if (args.length == 0) {
            String[] usageLines = Translation.translate("<lang>command_usage_tiktok</lang>", bread.getLanguage()).split("\n");
            for (String line : usageLines) {
                bread.sendMessage(line);
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "link":
                if (args.length < 2) {
                    return false;
                }
                String username = args[1].replace("@", "");
                if (!bread.getData("tiktok").isSet()) {
                    bread.setData("tiktok", username);
                    bread.sendMessage("<lang>tiktok_successfully_linked</lang>");
                    TikTokHandler.connect(player.getUniqueId(), username);
                } else {
                    bread.sendMessage("<lang>tiktok_already_linked</lang>");
                }
                break;
                
            case "unlink":
                if (bread.getData("tiktok").isSet()) {
                    TikTokHandler.disconnect(player.getUniqueId());
                    bread.setData("tiktok", null);
                    bread.sendMessage("<lang>tiktok_successfully_unlinked</lang>");
                } else {
                    bread.sendMessage("<lang>tiktok_not_linked</lang>");
                }
                break;
                
            default:
                return false;
        }
        
        return true;
    }
}