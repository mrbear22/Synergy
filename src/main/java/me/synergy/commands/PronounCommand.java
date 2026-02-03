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
import me.synergy.text.Gendered;

public class PronounCommand implements CommandExecutor, TabCompleter {
	public PronounCommand() {}
	
    public void initialize() {
    	if (!Synergy.getConfig().getBoolean("localizations.genders")) {
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
	        Set<String> pronouns = Gendered.getGendersAsStringSet();
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
        Set<String> pronouns = Gendered.getGendersAsStringSet();
        
        switch (label.toLowerCase()) {
            case "pronoun":
            case "gender":
                if (args.length > 0 && pronouns.contains(args[0].toUpperCase())) {
                    bread.setData("gender", args[0].toUpperCase());
                } else if (args.length == 0) {
                    return false;
                } else {
                    sender.sendMessage("<lang>invalid-gender</lang>"); 
                    return false;
                }
                break;
            case "iamgirl":
                bread.setData("gender", "FEMALE");
                break;
            case "iamboy":
                bread.setData("gender", "MALE");
                break;
            default:
                sender.sendMessage("<lang>unknown-command</lang>");
                return false;
        }
        sender.sendMessage("<lang>your-gender<arg>" + bread.getGender() + "</arg></lang>");
        return true;
    }
}