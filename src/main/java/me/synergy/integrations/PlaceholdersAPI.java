package me.synergy.integrations;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Cache;
import me.synergy.text.Color;
import me.synergy.text.Interactive;
import me.synergy.text.Translation;
import me.synergy.text.Gendered;
import me.synergy.text.Gendered.Gender;
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
            Gender gender = (bread == null) ? Gender.MALE : bread.getGender();
            
            //String result = new Locale("<locale:" + identifier + ">", language).setGendered(gender).getLegacyColored(theme);
           
            String result = Translation.translate("<locale:" + identifier + ">", language);
            result = Gendered.process(result, gender);
            result = Interactive.removeTags(result);
            result = Color.processThemeTags(result, theme);
            result = Color.processColorReplace(result, theme);
            result = Color.processCustomColorCodes(result);
            
            
            
            cache.add("placeholder:"+identifier, result, 10);

            return result;
        }
    }

}
