package me.synergy.integrations;

import me.synergy.modules.Module;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.synergy.brains.Synergy;
import me.synergy.handlers.ChatHandler;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Cache;
import me.synergy.text.Color;
import me.synergy.text.Interactive;
import me.synergy.text.Translation;
import me.synergy.text.Gendered;
import me.synergy.text.Gendered.Gender;
import net.md_5.bungee.api.ChatColor;

public class PlaceholdersAPI implements Module {

    @Override public String[] plugins() { return new String[] {"PlaceholderAPI"}; }

    private static volatile boolean enabled;

    @Override
    public void start() {
        new LocalesListener().register();
        new BreadDataListener().register();
        enabled = true;
    }

    @Override
    public void stop() {
        enabled = false;
    }

    public static boolean enabled() {
        return enabled;
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

            if (cache.has("placeholder:"+identifier)) {
                return cache.get("placeholder:"+identifier).getAsString();
            }

            BreadMaker bread = Synergy.getBread(player.getUniqueId());
            String result = resolveKnownPlaceholder(bread, identifier);

            if (result == null) {
                result = bread.getData(identifier).isSet() ? bread.getData(identifier).getAsString() : "none";
            }

            cache.add("placeholder:"+identifier, result, 10);
            
            return result;
        }

        private String resolveKnownPlaceholder(BreadMaker bread, String identifier) {
            return switch (identifier.toLowerCase()) {
                case "chat" -> bread.getData("chat").isSet() ? bread.getData("chat").getAsString() : "global";
                case "language" -> bread.getLanguage();
                case "theme" -> bread.getTheme();
                case "muted" -> String.valueOf(bread.isMuted());
                case "mute_reason" -> bread.getMuteReason() != null ? bread.getMuteReason() : "";
                case "mute_by" -> bread.getMuteBy() != null ? bread.getMuteBy() : "";
                case "mute_remaining" -> {
                    long remaining = bread.getMuteRemainingSeconds();
                    yield remaining == -1 ? "perm" : String.valueOf(remaining);
                }
                case "chat_locked" -> String.valueOf(ChatHandler.isChatLocked());
                case "slowmode" -> String.valueOf(ChatHandler.getSlowmodeSeconds());
                default -> null;
            };
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

            int lineIndex = -1;
            String baseIdentifier = identifier;

            int lastUnderscore = identifier.lastIndexOf('_');
            if (lastUnderscore != -1) {
                String suffix = identifier.substring(lastUnderscore + 1);
                try {
                    lineIndex = Integer.parseInt(suffix);
                    baseIdentifier = identifier.substring(0, lastUnderscore);
                } catch (NumberFormatException ignored) {
                }
            }

            Cache cache = new Cache(player.getUniqueId());

            if (cache.has("placeholder:" + identifier)) {
                return cache.get("placeholder:" + identifier).getAsString();
            }

            BreadMaker bread = Synergy.getBread(player.getUniqueId());
            String language = (bread == null) ? Translation.getDefaultLanguage() : bread.getLanguage();
            String theme    = (bread == null) ? Color.getDefaultTheme()          : bread.getTheme();
            Gender gender   = (bread == null) ? Gender.MALE                      : bread.getGender();

            String result = Translation.translate("<locale:" + baseIdentifier + ">", language);
            result = Gendered.process(result, gender);
            result = Interactive.removeTags(result);
            result = Color.processThemeTags(result, theme);
            result = Color.processColorReplace(result, theme);
            result = Color.processCustomColorCodes(result);
            result = PlaceholderAPI.setPlaceholders(player, result);

            if (lineIndex >= 0) {
                String[] lines = result.split("\n", -1);
                result = (lineIndex < lines.length) ? lines[lineIndex] : "";
            }

            cache.add("placeholder:" + identifier, result, 10);

            return result;
        }
    }

}