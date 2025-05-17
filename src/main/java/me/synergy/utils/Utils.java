package me.synergy.utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import me.clip.placeholderapi.PlaceholderAPI;
import me.synergy.brains.Synergy;
import net.md_5.bungee.api.ChatColor;

public class Utils {
	
    private static String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder randomString = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(CHARACTERS.length());
            char randomChar = CHARACTERS.charAt(randomIndex);
            randomString.append(randomChar);
        }
        return randomString.toString();
    }

    public static String removeRepetitiveCharacters(String sentence) {
        StringBuilder sb = new StringBuilder();
        char[] chars = sentence.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char currentChar = chars[i];
            int count = 1;
            while (i + 1 < chars.length && Character.toLowerCase(chars[i + 1]) == Character.toLowerCase(currentChar)) {
                count++;
                i++;
            }
            int repeatCount = Math.min(count, 3);
            for (int j = 0; j < repeatCount; j++) {
                sb.append(currentChar);
            }
        }
        return sb.toString();
    }

    public static String removeIgnoringCase(String word, String sentence) {
        String lowerCaseSentence = sentence.toLowerCase();
        String lowerCaseWord = word.toLowerCase();
        String[] words = lowerCaseSentence.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String w: words) {
            if (!w.equals(lowerCaseWord)) {
				result.append(w).append(" ");
			}
        }
        return result.toString().trim();
    }


    public static String stripColorTags(String string) {
        String pattern = "<#\\w{6}>";
        string = string.replaceAll(pattern, "").replaceAll("</#\\w{6}>", "");
        string = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', string));
        return string;
    }

    public static String[] splitMessage(String message) {
        List < String > parts = new ArrayList < > ();
        String[] words = message.split("\\s+");
        StringBuilder currentPart = new StringBuilder();
        for (String word: words) {
            if (currentPart.length() + word.length() + 1 <= 256) {
                currentPart.append(currentPart.length() > 0 ? " " : "").append(word);
            } else {
                parts.add(currentPart.toString());
                currentPart = new StringBuilder(word);
            }
        }
        parts.add(currentPart.toString());
        return parts.toArray(new String[0]);
    }

    public static void addBlockedWord(String word) {
    	List<String> words = getBlockedWorlds();
    	words.add(word);
    	Synergy.getSpigot().getConfig().set("chat-manager.chat-filter.blocked-words", words);
    	Synergy.getSpigot().saveConfig();
    }

    public static void removeBlockedWord(String word) {
    	List<String> words = getBlockedWorlds();
    	words.remove(word);
    	Synergy.getSpigot().getConfig().set("chat-manager.chat-filter.blocked-words", words);
    	Synergy.getSpigot().saveConfig();
    }

    public static void addIgnoredWord(String word) {
    	List<String> words = getIgnoredWorlds();
    	words.add(word);
    	Synergy.getSpigot().getConfig().set("chat-manager.chat-filter.ignored-words", words);
    	Synergy.getSpigot().saveConfig();
    }

    public static void removeIgnoredWord(String word) {
    	List<String> words = getIgnoredWorlds();
    	words.remove(word);
    	Synergy.getSpigot().getConfig().set("chat-manager.chat-filter.ignored-words", words);
    	Synergy.getSpigot().saveConfig();
    }


    public static List<String> getBlockedWorlds() {
		return Synergy.getSpigot().getConfig().getStringList("chat-manager.chat-filter.blocked-words");
	}

    public static List<String> getIgnoredWorlds() {
		return Synergy.getSpigot().getConfig().getStringList("chat-manager.chat-filter.ignored-words");
	}

    public static String censorBlockedWords(String sentence) {
        double tolerance = Synergy.getConfig().getDouble("chat-manager.chat-filter.blocked-words-tolerance-percentage");
        
        for (String blockedWord : getBlockedWorlds()) {
            String match = "";
            int start = 0, end = 0;
            
            for (int i = 0; i < removeColorCodes(sentence).length(); i++) {
                if (Character.isAlphabetic(removeColorCodes(sentence).charAt(i))) {
                    boolean isFirstLetterMatches = blockedWord.charAt(0) == removeColorCodes(sentence).charAt(i);
                    boolean isWordStartsWithBadWord = blockedWord.startsWith(match + removeColorCodes(sentence).charAt(i));
                    boolean isWordWithoutDuplicatesStartsWithBadWord = blockedWord.startsWith(removeConsecutiveDuplicates(match + removeColorCodes(sentence).charAt(i)));

                    if (isFirstLetterMatches || isWordStartsWithBadWord || isWordWithoutDuplicatesStartsWithBadWord) {
                        if (match.isEmpty()) {
                            start = i;
                        }
                        match += removeColorCodes(sentence).charAt(i);
                        end = i;
                    } else {
                        match = "";
                    }

                    if (blockedWord.equals(match) || blockedWord.equals(removeConsecutiveDuplicates(match))) {
                        String word = findWordInRange(removeColorCodes(sentence), start, end);

                        if (getIgnoredWorlds().contains(word)) {
                            continue;
                        }

                        double percentage = (double) match.length() / (double) word.length() * 100;
                        if (tolerance < percentage || !word.contains(blockedWord)) {
                            sentence = censorPartOfSentence(sentence, start, end);
                        }
                    }
                }
            }
        }
        return sentence;
    }

    public static String findWordInRange(String sentence, int start, int end) {
        String[] words = sentence.split("\\s+");
        
        for (String word : words) {
            int wordStart = sentence.indexOf(word, start - 5 >= 0 ? start - 5 : 0);
            int wordEnd = wordStart + word.length() - 1;

            if (start >= wordStart && end <= wordEnd) {
                return word;
            }
        }
        return sentence.substring(start, Math.min(end + 1, sentence.length()));
    }

    public static String censorPartOfSentence(String sentence, int start, int end) {
        if (end - start < 2) {
            return sentence;
        }
        
        char[] charArray = sentence.toCharArray();
        for (int i = start + 1; i < end; i++) { // Міняємо тільки символи між start і end
            if (Character.isAlphabetic(sentence.charAt(i))) {
                charArray[i] = '*';
            }
        }
        return new String(charArray);
    }
    
	public static String translateSmiles(String string) {
		for (Entry<String, Object> e : Synergy.getConfig().getConfigurationSection("chat-manager.custom-emojis").entrySet()) {
			string = string.replace(e.getKey(), Synergy.getConfig().getString("chat-manager.custom-emojis."+e.getKey()));
		}
		return string;
	}

    public static String removeColorCodes(String input) {
        String regex = "&[0-9a-f]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        String result = matcher.replaceAll("**");
        return result;
    }

    public static String removeConsecutiveDuplicates(String text) {
        StringBuilder result = new StringBuilder();
        char prevChar = '\0';
        for (char currentChar : text.toCharArray()) {
            if (currentChar != prevChar) {
                result.append(currentChar);
                prevChar = currentChar;
            }
        }
        return result.toString();
    }

    private static String applyGradientToText(String text, ChatColor startColor, ChatColor endColor) {
        StringBuilder gradientText = new StringBuilder();
        int startRgb = startColor.getColor().getRGB();
        int endRgb = endColor.getColor().getRGB();
        int textLength = text.length();
        int startR = (startRgb >> 16) & 0xFF;
        int startG = (startRgb >> 8) & 0xFF;
        int startB = startRgb & 0xFF;
        int endR = (endRgb >> 16) & 0xFF;
        int endG = (endRgb >> 8) & 0xFF;
        int endB = endRgb & 0xFF;
        double stepR = ((double)(endR - startR)) / textLength;
        double stepG = ((double)(endG - startG)) / textLength;
        double stepB = ((double)(endB - startB)) / textLength;

        for (int i = 0; i < textLength; i++) {
            int r = (int)(startR + stepR * i);
            int g = (int)(startG + stepG * i);
            int b = (int)(startB + stepB * i);
            ChatColor color = ChatColor.of(new java.awt.Color(r, g, b));
            gradientText.append(color).append(text.charAt(i));
        }
        return gradientText.toString();
    }

    public static String applyGradient(String text) {
        String pattern = "<(#\\w{6})>([^<]+?)</(#\\w{6})>";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(text);
        StringBuffer result = new StringBuffer();

        while (m.find()) {
            ChatColor startColor = ChatColor.of(m.group(1));
            ChatColor endColor = ChatColor.of(m.group(3));
            String tagText = m.group(2);
            String gradientText = applyGradientToText(tagText, startColor, endColor);
            m.appendReplacement(result, gradientText);
        }
        m.appendTail(result);
        return result.toString();
    }

	public static String replaceFirstAndLastQuotes(String input) {
	    if (input == null || input.isEmpty() || input.length() < 2) {
	        return input;
	    }
	    if (input.charAt(0) == '"') {
	        input = input.substring(1);
	    }
	    int lastIndex = input.length() - 1;
	    if (input.charAt(lastIndex) == '"') {
	        input = input.substring(0, lastIndex);
	    }
	    return input;
	}

	public static boolean isValidJson(String input) {
	    try {
	        JsonElement jsonElement = JsonParser.parseString(input);
	        return jsonElement.isJsonObject() || jsonElement.isJsonArray();
	    } catch (JsonSyntaxException e) {
	        return false;
	    }
	}

    public static String convertToJson(String input) {
    	input = replaceFirstAndLastQuotes(input);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("text", "");
        JsonArray extraArray = new JsonArray();
        extraArray.add(input);
        jsonObject.add("extra", extraArray);
        return jsonObject.toString();
    }

    public static String extractText(String json) {
        try {
        //    json = json.replaceAll("(?<=\\S)[\\n\\r]+", "\\\\n")
        //              .replaceAll("(?<=\\S)[\\r]+", "\\\\r")
        //              .replaceAll("[\\t]+", "\\\\t");

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(json);
            StringBuilder combinedText = new StringBuilder();
            extractText(rootNode, combinedText);
            return combinedText.toString();
        } catch (Exception c) {
            Synergy.getLogger().warning("Error while extracting text: " + c.getLocalizedMessage());
            return json;
        }
    }

    private static void extractText(JsonNode node, StringBuilder combinedText) {
        if (node.isObject()) {
            if (node.has("text")) {
                JsonNode textNode = node.get("text");
                if (textNode.isTextual()) {
                    combinedText.append(textNode.asText());
                }
            }
            if (node.has("extra")) {
                JsonNode extraNode = node.get("extra");
                if (extraNode.isArray()) {
                    for (JsonNode arrayElement : extraNode) {
                        extractText(arrayElement, combinedText);
                    }
                }
            }
        } else if (node.isArray()) {
            for (JsonNode arrayElement : node) {
                extractText(arrayElement, combinedText);
            }
        } else if (node.isTextual()) {
            combinedText.append(node.asText());
        }
    }

    public static JsonArray insertJsonElementIntoArray(int index, JsonElement val, JsonArray currentArray) {
        JsonArray newArray = new JsonArray();
        for (int i = 0; i < index; i++) {
            newArray.add(currentArray.get(i));
        }
        newArray.add(val);
        for (int i = index; i < currentArray.size(); i++) {
            newArray.add(currentArray.get(i));
        }
        return newArray;
    }
    
    public static List<String> getPlayers() {
    	List<String> players = new ArrayList<String>();
    	if (Synergy.isRunningSpigot()) {
    		Bukkit.getOnlinePlayers().stream().forEach(p -> players.add(p.getName()));
    	}
    	if (Synergy.isRunningBungee()) {
    		Synergy.getBungee().getProxy().getServers().entrySet().stream().forEach(s -> s.getValue().getPlayers().forEach(p -> players.add(p.getName())));
    	}
    	return players;
    }
    
    public static String replacePlaceholderOutputs(OfflinePlayer player, String format) {
        StringBuilder result = new StringBuilder(format);
        Set<Entry<String, Object>> placeholdersKeys = Synergy.getConfig().getConfigurationSection("placeholder-output-replacements").entrySet();

        for (Entry<String, Object> entry : placeholdersKeys) {
            String placeholderKey = entry.getKey();
            String replacementKey = "";
            try {
                replacementKey = PlaceholderAPI.setPlaceholders(player, placeholderKey);
            } catch (Exception e) {}
            Set<Entry<String, Object>> placeholders = Synergy.getConfig().getConfigurationSection("placeholder-output-replacements." + placeholderKey).entrySet();
            for (Entry<String, Object> p : placeholders) {
                String configKey = p.getKey();
                String configValue = (String) p.getValue();

                if (replacementKey.isEmpty() && configKey.equals("%none%")) {
                    replaceInResult(result, placeholderKey, configValue);
                } else if (!replacementKey.isEmpty() && configKey.equals(placeholderKey)) {
                    String formattedValue = configValue.replace(placeholderKey, replacementKey);
                    replaceInResult(result, placeholderKey, formattedValue);
                }
            }
        }

        return result.toString();
    }
    private static void replaceInResult(StringBuilder result, String placeholderKey, String replacementValue) {
        int index = result.indexOf(placeholderKey);
        while (index != -1) {
            result.replace(index, index + placeholderKey.length(), replacementValue);
            index = result.indexOf(placeholderKey, index + replacementValue.length());
        }
    }

}