package me.synergy.commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Endings;

public class PronounCommand implements CommandExecutor, TabCompleter {
	public PronounCommand() {}
	
    public void initialize() {
    	if (!Synergy.getConfig().getBoolean("localizations.pronouns")) {
	    	return;
	    }
        Synergy.getSpigot().getCommand("pronoun").setExecutor(this);
        Synergy.getSpigot().getCommand("iamboy").setExecutor(this);
        Synergy.getSpigot().getCommand("iamgirl").setExecutor(this);
        Synergy.getSpigot().getCommand("gender").setExecutor(this);
        Synergy.getSpigot().getCommand("pronoun").setTabCompleter(this);
	}
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    	if (args.length < 2) {
	        Set<String> pronouns = Endings.getPronounsAsStringSet();
	        return new ArrayList<>(pronouns);
    	}
    	return null;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("<lang>command-not-player</lang>");
            return false;
        }
        
        Player player = (Player) sender;
        BreadMaker bread = new BreadMaker(player.getUniqueId());
        Set<String> pronouns = Endings.getPronounsAsStringSet();
        
        switch (label.toLowerCase()) {
            case "pronoun":
            case "gender":
                if (args.length > 0 && pronouns.contains(args[0].toUpperCase())) {
                    bread.setData("pronoun", args[0].toUpperCase());
                } else if (args.length == 0) {
                    return false; // Show usage from plugin.yml
                } else {
                    sender.sendMessage("<lang>invalid-pronoun</lang>"); 
                    return false;
                }
                break;
            case "iamgirl":
                bread.setData("pronoun", "SHE");
                break;
            case "iamboy":
                bread.setData("pronoun", "HE");
                break;
            default:
                sender.sendMessage("<lang>unknown-command</lang>");
                return false;
        }
        sender.sendMessage("<lang>your-pronoun<arg>" + bread.getPronoun() + "</arg></lang>");
        return true;
    }
}