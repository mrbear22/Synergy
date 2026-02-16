package me.synergy.commands;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Translation;

public class DiscordCommand implements CommandExecutor, TabCompleter, Listener, SynergyListener {

    public void initialize() {
        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
            // return;
        }

        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getSpigot().getCommand("discord").setExecutor(this);
        Synergy.getSpigot().getCommand("discord").setTabCompleter(this);
        Synergy.getEventManager().registerEvents(this);
        
        Locales.addDefault("command_description_discord", "en", "Discord integration");
        Locales.addDefault("command_usage_discord", "en", new String[] {
		    "<danger>Usage: /discord <action> [discord_account]",
		    "",
		    "<secondary>Actions:",
		    "<primary>  link    <secondary>- Link Minecraft to Discord",
		    "<primary>  unlink  <secondary>- Unlink Discord account", 
		    "<primary>  confirm <secondary>- Confirm linking process",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  discord_account <secondary>- Your Discord username or ID",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /discord link YourUsername",
		    "<primary>  /discord confirm",
		    "<primary>  /discord unlink",
		    "",
		    "<secondary>Join our Discord: <click:open_url:'%INVITE%'><hover:show_text:Click to open><secondary><u>%INVITE%</u></click>"
		});
    }

    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
        if (event.getOption("tags").isSet()) {
            Discord.getUsersTagsCache().clear();
            for (String tag : event.getOption("tags").getAsString().split(",")) {
                Discord.getUsersTagsCache().add(tag);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("link", "unlink", "confirm");
        } else if (args.length == 2 && args[0].equals("link")) {
            return Discord.getUsersTagsCache().stream()
                    .filter(u -> u.startsWith(args[1]))
                    .collect(Collectors.toList());
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
        
        if (!player.hasPermission("synergy.discord")) {
            sender.sendMessage("<lang>no-permission</lang>");
            return false;
        }
        
        if (args.length == 0) {
            String usage = Translation.translate("<lang>command_usage_discord</lang>", bread.getLanguage())
                    .replace("%INVITE%", Synergy.getConfig().getString("discord.invite-link"));
            String[] usageLines = usage.split("\n");
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
                Synergy.event("make-discord-link")
                    .setPlayerUniqueId(player.getUniqueId())
                    .setOption("tag", args[1])
                    .send();
                break;
                
            case "confirm":
                Synergy.event("confirm-discord-link")
                    .setPlayerUniqueId(player.getUniqueId())
                    .send();
                break;
                
            case "unlink":
                Synergy.event("remove-discord-link")
                    .setPlayerUniqueId(player.getUniqueId())
                    .send();
                Synergy.event("sync-roles-from-discord-to-mc")
	                .setPlayerUniqueId(player.getUniqueId())
	                .send();
                if (Synergy.getConfig().getBoolean("discord.kick-player.if-has-no-link.enabled")) {
                    bread.kick(Synergy.getConfig().getString("discord.kick-player.if-has-no-link.message"));
                }
                break;
                
            default:
                return false;
        }
        return true;
    }
}