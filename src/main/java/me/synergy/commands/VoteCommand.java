package me.synergy.commands;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.synergy.brains.Synergy;
import me.synergy.objects.LocaleBuilder;
import me.synergy.utils.BookMessage;

public class VoteCommand implements CommandExecutor {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("votifier.enabled")) return;
        Synergy.getSpigot().getCommand("vote").setExecutor(this);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(LocaleBuilder.of("command-not-player").fallback("<red>This command can only be used by players.").build());
            return false;
        }
        
        if (!sender.hasPermission("synergy.vote")) {
            sender.sendMessage(LocaleBuilder.of("no-permission").build());
            return false;
        }
        
        Player player = (Player) sender;
        List<String> monitorings = Synergy.getConfig().getStringList("votifier.monitorings");
        
        StringBuilder list = new StringBuilder();
        
        for (String m : monitorings) {
            try {
                String domain = new URI(m).getHost();
                String shortenDomain = domain.replace("www.", "");
                list.append(LocaleBuilder.of("vote-monitorings-format")
                    .placeholder("url", m)
                    .placeholder("monitoring", shortenDomain)
                    .fallback("<click:open_url:'%url%'><green>%monitoring%</click>")
                    .build());
                list.append("\n");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        
        String content = LocaleBuilder.of("monitorings-menu")
            .placeholder("monitorings", list.toString())
            .fallback("<yellow>Available voting sites:\n%monitorings%")
            .build();
        
        BookMessage.sendFakeBook(player, "Monitorings", content);
        
        return true;
    }
}