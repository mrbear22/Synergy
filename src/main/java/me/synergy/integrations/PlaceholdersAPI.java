package me.synergy.integrations;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Cache;
import me.synergy.text.Color;
import me.synergy.text.Interactive;
import me.synergy.utils.Endings;
import me.synergy.utils.Translation;
import me.synergy.utils.Endings.Pronoun;
import net.md_5.bungee.api.ChatColor;

public class PlaceholdersAPI {

    public void initialize() {
        new LocalesListener().register();
        new BreadDataListener().register();
    }
    
    public static String processPlaceholders(Player player, String string) {
        if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(player, string);
        }
        return string;
    }
    
    public class BreadDataListener extends PlaceholderExpansion {

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public String getAuthor() {
            return "mrbear22";
        }

        @Override
        public String getIdentifier() {
            return "breadmaker";
        }

        @Override
        public String getPlugin() {
            return null;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        public String setPlaceholders(Player player, String placeholder) {
            return ChatColor.translateAlternateColorCodes('&', PlaceholderAPI.setPlaceholders(player, placeholder));
        }

        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
	
        	if (player == null) {
        		return identifier;
        	}
        	
        	Cache cache = new Cache(player.getUniqueId());

            if (!cache.isExpired("placeholder:"+identifier)) {
                return cache.get("placeholder:"+identifier).getAsString();
            }

            BreadMaker bread = Synergy.getBread(player.getUniqueId());
            String result = bread.getData(identifier).isSet() ? bread.getData(identifier).getAsString() : "none";

            cache.add("placeholder:"+identifier, result, 10);
            
            return result;
        }

    }
    
    public class LocalesListener extends PlaceholderExpansion {

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public String getAuthor() {
            return "mrbear22";
        }

        @Override
        public String getIdentifier() {
            return "translation";
        }

        @Override
        public String getPlugin() {
            return null;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        public String setPlaceholders(Player player, String placeholder) {
            return ChatColor.translateAlternateColorCodes('&', PlaceholderAPI.setPlaceholders(player, placeholder));
        }

        @Override
        public String onPlaceholderRequest(Player player, String identifier) {

        	if (player == null) {
        		return identifier;
        	}
        	
            Cache cache = new Cache(player.getUniqueId());
            
            if (!cache.isExpired("placeholder:"+identifier)) {
                return cache.get("placeholder:"+identifier).getAsString();
            }

            BreadMaker bread = (player == null) ? null : Synergy.getBread(player.getUniqueId());
            String language = (bread == null) ? Translation.getDefaultLanguage() : bread.getLanguage();
            String theme = (bread == null) ? Color.getDefaultTheme() : bread.getTheme();
            Pronoun pronoun = (bread == null) ? Pronoun.HE : bread.getPronoun();
            
            //String result = new Locale("<lang>" + identifier + "</lang>", language).setExecuteInteractive(bread).setEndings(pronoun).getLegacyColored(theme);
           
            String result = Translation.translate("<lang>" + identifier + "</lang>", language);
            result = Endings.processEndings(result, pronoun);
            result = Interactive.removeTags(result);
            result = Color.processThemeTags(result, theme);
            result = Color.processColorReplace(result, theme);
            result = Color.processCustomColorCodes(result);
            
            cache.add("placeholder:"+identifier, result, 10);

            return result;
        }
    }

}
