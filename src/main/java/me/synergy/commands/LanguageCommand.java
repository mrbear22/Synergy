package me.synergy.commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.synergy.brains.Synergy;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.utils.BookMessage;

public class LanguageCommand implements CommandExecutor, TabCompleter {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("localizations.enabled")) {
            return;
        }
        Synergy.getSpigot().getCommand("language").setExecutor(this);
        Synergy.getSpigot().getCommand("lang").setExecutor(this);
        Synergy.getSpigot().getCommand("language").setTabCompleter(this);
        Synergy.getSpigot().getCommand("lang").setTabCompleter(this);
        
        Locales.addDefault("command_description_language", "en", "Change your language");
        Locales.addDefault("command_usage_language", "en", new String[] {
		    "<danger>Usage: /language [language_code]",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  language_code <secondary>- (Optional) Language code",
		    "",
		    "<secondary>Available languages:",
		    "<primary>  en <secondary>- English",
		    "<primary>  ua <secondary>- Ukrainian",
		    "",
		    "<secondary>Examples:",
		    "<primary>  /language ua <secondary>- Switch to Ukrainian",
		    "<primary>  /language    <secondary>- Open language selector"
		});
        Locales.addDefault("languages", "en", new String[] {
			"<danger>Choose your language",
			"",
			"<primary>▶ <click:run_command:/language auto><hover:show_text:Use your game language><danger>[AUTO]</click><primary>",
			"<primary>▶ <click:run_command:/language en><hover:show_text:Click to select><danger>[ENGLISH]</click><primary>",
			"<primary>▶ <click:run_command:/language uk><hover:show_text:Натисни, щоб вибрати><danger>[УКРАЇНСЬКА]</click><primary>",
			"",
			"<lang>language-auto</lang>",
			"<secondary>"
		});
        Locales.addDefault("selected-language", "en", "<success>Language selected: <primary>%language%<sound:'entity.ocelot.death'>");
        Locales.addDefault("language-auto", "en", "<secondary>Server automatically detects your game language");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length < 2) {
            Set<String> languages = new HashSet<>(Synergy.getLocalesManager().getLanguages());
            languages.add("auto");
            return new ArrayList<>(languages);
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
        Set<String> languages = Synergy.getLocalesManager().getLanguages();
        
        if (args.length > 0 && (languages.contains(args[0].toLowerCase()) || args[0].equalsIgnoreCase("auto"))) {
            bread.setData("language", args[0].equalsIgnoreCase("auto") ? null : args[0].toLowerCase());
            sender.sendMessage(LocaleBuilder.of("selected-language").placeholder("language", args[0]).build());
            return true;
        } else if (args.length > 0) {
            return false;
        }
        
        BookMessage.sendFakeBook(player, "Languages", LocaleBuilder.of("languages").build());
        return true;
    }
}