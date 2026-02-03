package me.synergy.text;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class Gendered {
    
    public enum Gender {
        MALE, FEMALE, NONBINARY
    }
    
    public static Gender getGender(String gender) {
        try {
            return Gender.valueOf(gender.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Gender.MALE;
        }
    }
    
    public static Set<Gender> getGenders() {
        return EnumSet.allOf(Gender.class);
    }
    
    public static Set<String> getGendersAsStringSet() {
        return getGenders().stream()
                .map(Gender::name)
                .collect(Collectors.toSet());
    }

    private static Gender extractGenderFromTag(String input) {
        Pattern genderPattern = Pattern.compile("<gender:(?:')?(male|female|nonbinary)(?:')?>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = genderPattern.matcher(input);
        
        if (matcher.find()) {
            String genderStr = matcher.group(1);
            return getGender(genderStr);
        }
        
        return null;
    }
    
    public static String removeGenderTag(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("(?i)<gender:(?:')?(?:male|female|nonbinary)(?:')?>", "");
    }

    private static Gender detectGenderFromText(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        
        String[] words = input.split("\\s+");
        
        for (String word : words) {
            for (UUID playerUUID : getOnlinePlayerUUIDs()) {
                String playerName = Synergy.getBread(playerUUID).getName();
                if (playerName != null && word.contains(playerName)) {
                    return Synergy.getBread(playerUUID).getGender();
                }
            }
        }
        
        return null;
    }

    public static String process(String input, Gender gender) {
        if (input == null) {
            return null;
        }
        
        if (gender == null) {
            gender = extractGenderFromTag(input);
        }
        
        if (gender == null) {
            gender = detectGenderFromText(input);
        }
        
        if (gender == null) {
            gender = Gender.MALE;
        }
        
        input = removeGenderTag(input);
        input = processGenderTags(input, gender);
        
        return input;
    }
    
    private static String processGenderTags(String input, Gender gender) {
        for (Gender g : Gender.values()) {
            String tagName = g.name().toLowerCase();
            Pattern pattern = Pattern.compile("<" + tagName + ":(?:'([^']*)'|([^>]*))>");
            Matcher matcher = pattern.matcher(input);
            
            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                String replacement = (g == gender) ? content : "";
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);
            input = result.toString();
        }
        
        Pattern shortPattern = Pattern.compile("<(m|f|nb):(?:'([^']*)'|([^>]*))>", Pattern.CASE_INSENSITIVE);
        Matcher shortMatcher = shortPattern.matcher(input);
        
        StringBuffer result = new StringBuffer();
        while (shortMatcher.find()) {
            String shortForm = shortMatcher.group(1).toLowerCase();
            String content = shortMatcher.group(2) != null ? shortMatcher.group(2) : shortMatcher.group(3);
            
            Gender shortGender = null;
            if (shortForm.equals("m")) {
                shortGender = Gender.MALE;
            } else if (shortForm.equals("f")) {
                shortGender = Gender.FEMALE;
            } else if (shortForm.equals("nb")) {
                shortGender = Gender.NONBINARY;
            }
            
            String replacement = (shortGender == gender) ? content : "";
            shortMatcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        shortMatcher.appendTail(result);
        input = result.toString();
        
        return input;
    }

    private static Collection<UUID> getOnlinePlayerUUIDs() {
        Collection<UUID> playerUUIDs = new HashSet<>();
        if (Synergy.isRunningSpigot()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerUUIDs.add(player.getUniqueId());
            }
        } else if (Synergy.isRunningBungee()) {
            for (ProxiedPlayer player : net.md_5.bungee.api.ProxyServer.getInstance().getPlayers()) {
                playerUUIDs.add(player.getUniqueId());
            }
        } else if (Synergy.isRunningVelocity()) {
            com.velocitypowered.api.proxy.ProxyServer proxy = Velocity.getProxy();
            proxy.getAllPlayers().forEach(player -> playerUUIDs.add(player.getUniqueId()));
        }
        return playerUUIDs;
    }
}