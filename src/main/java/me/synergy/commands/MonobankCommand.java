package me.synergy.commands;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Translation;

public class MonobankCommand implements CommandExecutor, TabCompleter, Listener {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("monobank.enabled")) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getSpigot().getCommand("monobank").setExecutor(this);
        Synergy.getSpigot().getCommand("monobank").setTabCompleter(this);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("link", "unlink");
        }
        if (args[0].equalsIgnoreCase("link") && args.length == 2) {
            return List.of("<token>");
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

        if (!player.hasPermission("synergy.monobank")) {
            sender.sendMessage("<lang>synergy_no_permission</lang>");
            return false;
        }

        if (args.length == 0) {
            // Send usage directly in chat, not as a book
            String[] usageLines = Translation.translate("<lang>command_usage_monobank</lang>", bread.getLanguage()).split("\n");
            for (String line : usageLines) {
                bread.sendMessage(line);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "link":
                if (args.length < 2) {
                    return false; // Show usage from plugin.yml
                }
                if (!bread.getData("monobank").isSet()) {
                    bread.setData("monobank", args[1]);
                    bread.sendMessage("<lang>monobank_successfully_linked</lang>");
                } else {
                    bread.sendMessage("<lang>monobank_already_linked</lang>");
                }
                break;
                
            case "unlink":
                if (bread.getData("monobank").isSet()) {
                    bread.setData("monobank", null);
                    bread.sendMessage("<lang>monobank_successfully_unlinked</lang>");
                } else {
                    bread.sendMessage("<lang>monobank_not_linked</lang>");
                }
                break;
                
            default:
                return false;
        }
        return true;
    }
}