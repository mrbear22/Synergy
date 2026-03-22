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
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Translation;
import me.synergy.web.TikTokHandler;

public class TikTokCommand implements CommandExecutor, TabCompleter, Listener {
    
    public void initialize() {

        Locales.addDefault("command_description_tiktok", "en", "Tiktok integration");
        Locales.addDefault("command_usage_tiktok", "en", new String[] {
		    "<danger>Usage: /tiktok <action> [arguments]",
		    "",
		    "<secondary>Actions:",
		    "<primary>  link <@username>      <secondary>- Link Minecraft to Tiktok",
		    "<primary>  unlink               <secondary>- Unlink Tiktok account", 
		    "<primary>  connect              <secondary>- Reconnect to TikTok Live",
		    "<primary>  disconnect           <secondary>- Disconnect from TikTok Live",
		    "<primary>  testreward <type>    <secondary>- Test reward execution",
		    "",
		    "<secondary>Test Reward Types:",
		    "<primary>  gift <name>          <secondary>- Test gift reward",
		    "<primary>  like <count>         <secondary>- Test like reward",
		    "<primary>  follow               <secondary>- Test follow reward",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /tiktok link @usertag",
		    "<primary>  /tiktok connect",
		    "<primary>  /tiktok testreward gift Rose",
		    "<primary>  /tiktok testreward like 150",
		    ""
		});
        
        Locales.addDefault("tiktok_link_usage", "en", "<danger>Usage: /tiktok link <@usertag>");
        Locales.addDefault("tiktok_successfully_linked", "en", "<success>Tiktok account linked!");
        Locales.addDefault("tiktok_already_linked", "en", "<danger>Tiktok account already linked!");
        Locales.addDefault("tiktok_successfully_unlinked", "en", "<success>Tiktok account unlinked!");
        Locales.addDefault("tiktok_not_linked", "en", "<danger>No linked tiktok account!");
        Locales.addDefault("tiktok_connecting", "en", "<secondary>Connecting to TikTok Live...");
        Locales.addDefault("tiktok_disconnecting", "en", "<secondary>Disconnecting from TikTok Live...");
        Locales.addDefault("tiktok_testreward_usage", "en", "<danger>Usage: /tiktok testreward <gift|like|follow> [value]");
        Locales.addDefault("tiktok_testreward_executed", "en", "<success>Test reward executed!");
        
        if (!Config.addDefault("tiktok.enabled", false)) return;

        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getSpigot().getCommand("tiktok").setExecutor(this);
        Synergy.getSpigot().getCommand("tiktok").setTabCompleter(this);
        
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
            return List.of("link", "unlink", "connect", "disconnect", "testreward");
        }
        if (args[0].equalsIgnoreCase("link") && args.length == 2) {
            return List.of("<username>");
        }
        if (args[0].equalsIgnoreCase("testreward") && args.length == 2) {
            return List.of("gift", "like", "follow");
        }
        if (args[0].equalsIgnoreCase("testreward") && args.length == 3) {
            if (args[1].equalsIgnoreCase("gift")) {
                return List.of("<gift_name>");
            }
            if (args[1].equalsIgnoreCase("like")) {
                return List.of("<like_count>");
            }
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
                    bread.sendMessage("<lang>tiktok_link_usage</lang>");
                    return true;
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
                
            case "connect":
                if (!bread.getData("tiktok").isSet()) {
                    bread.sendMessage("<lang>tiktok_not_linked</lang>");
                    return true;
                }
                bread.sendMessage("<lang>tiktok_connecting</lang>");
                TikTokHandler.connect(player.getUniqueId(), bread.getData("tiktok").getAsString());
                break;
                
            case "disconnect":
                bread.sendMessage("<lang>tiktok_disconnecting</lang>");
                TikTokHandler.disconnect(player.getUniqueId());
                break;
                
            case "testreward":
                if (args.length < 2) {
                    bread.sendMessage("<lang>tiktok_testreward_usage</lang>");
                    return true;
                }
                
                switch (args[1].toLowerCase()) {
                    case "gift":
                        if (args.length < 3) {
                            bread.sendMessage("<danger>Usage: /tiktok testreward gift <gift_name>");
                            return true;
                        }
                        String giftName = args[2];
                        Synergy.event("tiktok-gift")
                            .setPlayerUniqueId(player.getUniqueId())
                            .setOption("player-name", player.getName())
                            .setOption("gift-name", giftName)
                            .setOption("gift-id", "999")
                            .setOption("combo-count", "1")
                            .setOption("viewer-name", "TestDonater")
                            .send();
                        bread.sendMessage("<lang>tiktok_testreward_executed</lang>");
                        break;
                        
                    case "like":
                        if (args.length < 3) {
                            bread.sendMessage("<danger>Usage: /tiktok testreward like <count>");
                            return true;
                        }
                        try {
                            int likeCount = Integer.parseInt(args[2]);
                            Synergy.event("tiktok-like")
                                .setPlayerUniqueId(player.getUniqueId())
                                .setOption("player-name", player.getName())
                                .setOption("viewer-name", "TestViewer")
                                .setOption("like-count", String.valueOf(likeCount))
                                .send();
                            bread.sendMessage("<lang>tiktok_testreward_executed</lang>");
                        } catch (NumberFormatException e) {
                            bread.sendMessage("<danger>Invalid number: " + args[2]);
                        }
                        break;
                        
                    case "follow":
                        Synergy.event("tiktok-follow")
                            .setPlayerUniqueId(player.getUniqueId())
                            .setOption("player-name", player.getName())
                            .setOption("viewer-name", "TestFollower")
                            .send();
                        bread.sendMessage("<lang>tiktok_testreward_executed</lang>");
                        break;
                        
                    default:
                        bread.sendMessage("<lang>tiktok_testreward_usage</lang>");
                        break;
                }
                break;
                
            default:
                String[] usageLines = Translation.translate("<lang>command_usage_tiktok</lang>", bread.getLanguage()).split("\n");
                for (String line : usageLines) {
                    bread.sendMessage(line);
                }
                break;
        }
        
        return true;
    }
}