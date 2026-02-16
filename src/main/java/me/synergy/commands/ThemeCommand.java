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
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.utils.BookMessage;

public class ThemeCommand implements CommandExecutor, TabCompleter {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("localizations.enabled")) return;
        Synergy.getSpigot().getCommand("theme").setExecutor(this);
        Synergy.getSpigot().getCommand("theme").setTabCompleter(this);
        Locales.addDefault("themes", "en", new String[] {
			"   <danger><bold>Choose a theme:</bold>",
			"",
			"<primary>▶<click:run_command:/theme default><hover:show_text:Click to select><#dd8ea3>Default</click>",
			"<secondary>"
		});
        Locales.addDefault("command_description_theme", "en", "Change your theme");
        Locales.addDefault("command_usage_theme", "en", new String[] {
		    "<danger>Usage: /theme [theme_name]",
		    "",
		    "<secondary>Arguments:",
		    "<primary>  theme_name <secondary>- (Optional) Theme to apply",
		    "",
		    "<secondary>Use '/theme' to see all themes"
		});
        Locales.addDefault("selected-theme", "en", "<success>Theme selected: <primary>%theme%<sound:'entity.ocelot.death'>");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length < 2) {
            Set<String> themes = Synergy.getConfig().getConfigurationSection("localizations.color-themes").keySet();
            return new ArrayList<>(themes);
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
        Set<String> themes = Synergy.getConfig().getConfigurationSection("localizations.color-themes").keySet();
        
        if (args.length > 0 && (themes.contains(args[0].toLowerCase()) || args[0].equalsIgnoreCase("auto"))) {
            bread.setData("theme", args[0].equalsIgnoreCase("auto") ? null : args[0].toLowerCase());
            sender.sendMessage(LocaleBuilder.of("selected-theme").placeholder("theme", args[0]).build());
            return true;
        } else if (args.length > 0) {
            return false;
        }
        
        BookMessage.sendFakeBook(player, "Themes", LocaleBuilder.of("themes").build());
        return true;
    }
}