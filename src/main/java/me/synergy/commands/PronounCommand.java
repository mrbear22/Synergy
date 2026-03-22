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
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Gendered;
public class PronounCommand implements CommandExecutor, TabCompleter {
	public PronounCommand() {}
	
    public void initialize() {
    	if (!Config.getBoolean("localizations.genders")) {
	    	return;
	    }
        Synergy.getSpigot().getCommand("pronoun").setExecutor(this);
        Synergy.getSpigot().getCommand("iamboy").setExecutor(this);
        Synergy.getSpigot().getCommand("iamgirl").setExecutor(this);
        Synergy.getSpigot().getCommand("gender").setExecutor(this);
        Synergy.getSpigot().getCommand("pronoun").setTabCompleter(this);
        
        Locales.addDefault("command_description_pronoun", "en", "Set your pronouns");
        Locales.addDefault("command_usage_pronoun", "en", new String[] {
		    "<danger>Usage: /pronoun [pronoun_type]",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  pronoun_type <secondary>- (Optional) Preferred pronoun set",
		    "",
		    "<secondary>Available pronouns:",
		    "<primary>  he   <secondary>- He/Him pronouns",
		    "<primary>  she  <secondary>- She/Her pronouns", 
		    "<primary>  they <secondary>- They/Them pronouns",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /pronoun she <secondary>- Set She/Her pronouns"
		});
        
        Locales.addDefault("your-gender", "en", "<primary>Your gender: <secondary>%gender%");
        Locales.addDefault("invalid-pronoun", "en", "<danger>Invalid pronoun! Use available options.");
	}

    private String getPermissionForGender(String gender) {
        switch (gender.toUpperCase()) {
            case "MALE":   return "synergy.gender.male";
            case "FEMALE": return "synergy.gender.female";
            default:       return "synergy.gender.nonbinary";
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    	if (args.length < 2) {
            List<String> allowed = new ArrayList<>();
            for (String pronoun : Gendered.getGendersAsStringSet()) {
                String perm = getPermissionForGender(pronoun);
                if (sender.hasPermission(perm)) {
                    allowed.add(pronoun);
                }
            }
            return allowed;
    	}
    	return null;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(LocaleBuilder.of("command-not-player").build());
            return false;
        }
        
        Player player = (Player) sender;
        BreadMaker bread = new BreadMaker(player.getUniqueId());
        Set<String> pronouns = Gendered.getGendersAsStringSet();
        
        switch (label.toLowerCase()) {
            case "pronoun":
            case "gender":
                if (args.length > 0 && pronouns.contains(args[0].toUpperCase())) {
                    String gender = args[0].toUpperCase();
                    if (!player.hasPermission(getPermissionForGender(gender))) {
                        sender.sendMessage(LocaleBuilder.of("no-permission").build());
                        return false;
                    }
                    bread.setData("gender", gender);
                } else if (args.length == 0) {
                    return false;
                } else {
                    sender.sendMessage(LocaleBuilder.of("invalid-gender").build()); 
                    return false;
                }
                break;
            case "iamgirl":
                if (!player.hasPermission("synergy.gender.female")) {
                    sender.sendMessage(LocaleBuilder.of("no-permission").build());
                    return false;
                }
                bread.setData("gender", "FEMALE");
                break;
            case "iamboy":
                if (!player.hasPermission("synergy.gender.male")) {
                    sender.sendMessage(LocaleBuilder.of("no-permission").build());
                    return false;
                }
                bread.setData("gender", "MALE");
                break;
            default:
                sender.sendMessage(LocaleBuilder.of("unknown-command").build());
                return false;
        }
        sender.sendMessage(LocaleBuilder.of("your-gender").placeholder("gender", bread.getGender()).build());
        return true;
    }
}