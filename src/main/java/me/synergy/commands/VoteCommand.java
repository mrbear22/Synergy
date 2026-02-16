package me.synergy.commands;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.synergy.brains.Synergy;
import me.synergy.modules.Locales;
import me.synergy.objects.LocaleBuilder;
import me.synergy.utils.BookMessage;

public class VoteCommand implements CommandExecutor {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("votifier.enabled")) return;
        Synergy.getSpigot().getCommand("vote").setExecutor(this);
		
        Locales.addDefault("vote-monitorings-format", "en", "<primary>▶ <click:open_url:%URL%><hover:show_text:Click to vote><secondary><u>%MONITORING%</u></click>");
        Locales.addDefault("monitorings-menu", "en", new String[] {
			"<secondary>Vote for our server on these sites:",
			"",
			"%MONITORINGS%",
			"<primary>Thank you for supporting our server!"
		});
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(LocaleBuilder.of("command-not-player").build());
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
                    .build());
                list.append("\n");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        
        String content = LocaleBuilder.of("monitorings-menu")
            .placeholder("monitorings", list.toString())
            .build();
        
        BookMessage.sendFakeBook(player, "Monitorings", content);
        
        return true;
    }
}