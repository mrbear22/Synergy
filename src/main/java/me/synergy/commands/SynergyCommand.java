package me.synergy.commands;

import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import me.synergy.brains.Synergy;
import me.synergy.utils.Timings;

public class SynergyCommand implements CommandExecutor {

    public void initialize() {
        Synergy.getSpigot().getCommand("synergy").setExecutor(this);
    }

    @Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (args[0]) {
            case "reload":
                if (sender.hasPermission("synergy.reload")) {
                //	Synergy.getDiscord().shutdown();
                	Synergy.getSpigot().reloadConfig();
                    Synergy.getLocalesManager().initialize();
                    Synergy.getDataManager().initialize();
                    Synergy.getConfig().initialize();
                //  Synergy.getDiscord().initialize();
                    sender.sendMessage("<lang>synergy-reloaded</lang>");
                    return true;
                }
                sender.sendMessage("<lang>synergy-no-permission</lang>");
                return true;
            case "timings":
                if (sender.hasPermission("synergy.timings")) {
	                Map<String, Double> averages = new Timings().getAllAverages();
	                if (averages.isEmpty()) {
	                    sender.sendMessage(ChatColor.RED + "No timings recorded yet.");
	                    return true;
	                }
	                sender.sendMessage(ChatColor.YELLOW + "=== Timings Report ===");
	                averages.forEach((id, avg) -> 
	                    sender.sendMessage(ChatColor.YELLOW + id + ": " + getTimingColor(avg) + String.format("%.2f ms", avg))
	                );
                    return true;
                }
                sender.sendMessage("<lang>synergy-no-permission</lang>");
                return true;
        }
        return true;
    }
	
	private String getTimingColor(double avg) {
	    return avg < 5 ? ChatColor.GREEN.toString() : 
	           avg < 20 ? ChatColor.YELLOW.toString() : 
	           ChatColor.RED.toString();
	}

}