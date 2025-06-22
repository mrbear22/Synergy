package me.synergy.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import me.synergy.brains.Synergy;

public class Interactive {

    public static String processInteractive(String input) {

		if (!input.contains("<interactive>")) {
			return input;
		}

    	try {
    		String json;
    		if (!JsonUtils.isValidJson(input)) {
    			JsonObject baseJson = new JsonObject();
    			baseJson.addProperty("text", "");
    			JsonArray extraArray = new JsonArray();
    			JsonObject textElement = new JsonObject();
    			textElement.addProperty("text", input);
    			extraArray.add(textElement);
    			baseJson.add("extra", extraArray);
    			json = baseJson.toString();
    		} else {
    			json = input;
    		}
    		return processInteractiveTags(json);
    	} catch (Exception c) {
    		Synergy.getLogger().error("Error while processing interactive: " + c.getLocalizedMessage());
    		c.printStackTrace();
    		
    		try {
    			JsonObject fallbackJson = new JsonObject();
    			fallbackJson.addProperty("text", "");
    			JsonArray extraArray = new JsonArray();
    			JsonObject textElement = new JsonObject();
    			textElement.addProperty("text", input);
    			extraArray.add(textElement);
    			fallbackJson.add("extra", extraArray);
    			return processInteractiveTags(fallbackJson.toString());
    		} catch (Exception e) {
    			return removeInteractiveTags(input);
    		}
    	}

    }

    private static String processInteractiveTags(String json) {
    	try {
	        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
	        JsonArray extraArray = jsonObject.getAsJsonArray("extra");
	        JsonArray dividedExtra = new JsonArray();
	        for (JsonElement element : extraArray) {
	            if (element instanceof JsonPrimitive && ((JsonPrimitive) element).isString()) {
	                JsonObject text = new JsonObject();
	                text.addProperty("text", ((JsonPrimitive) element).getAsString());
	                splitInteractiveText(text, dividedExtra);
	            } else if (element instanceof JsonObject) {
	                JsonObject obj = (JsonObject) element;
	                splitInteractiveText(obj, dividedExtra);
	            }
	        }
	        jsonObject.remove("extra");
	        jsonObject.add("extra", dividedExtra);
	        return jsonObject.toString();
    	} catch (Exception c) {
    		Synergy.getLogger().error("Error while processing interactive: " + c.getLocalizedMessage());
    		c.printStackTrace();
    	}

    	return removeInteractiveTags(json);
    }

    public static String removeInteractiveTags(String string) {
    	return string.replaceAll("<interactive>(.*?)</interactive>", "$1")
    			.replaceAll("<hover>(.*?)</hover>", "")
    			.replaceAll("<url>(.*?)</url>", "")
    			.replaceAll("<command>(.*?)</command>", "")
    			.replaceAll("<suggest>(.*?)</suggest>", "")
    			.replaceAll("<copy>(.*?)</copy>", "")
    			.replaceAll("<sound>(.*?)</sound>", "")
    			.replaceAll("<title>(.*?)</title>", "")
    			.replaceAll("<subtitle>(.*?)</subtitle>", "")
    			.replaceAll("<toast>(.*?)</toast>", "");
    }

    private static void splitInteractiveText(JsonObject object, JsonArray dividedExtra) {
        String text = object.get("text").getAsString();
        int startIndex = text.indexOf("<interactive>");
        if (startIndex == -1) {
            JsonObject obj = new JsonObject();
            obj.addProperty("text", text);
            inheritProperties(object, obj);
            dividedExtra.add(obj);
        } else {
            int endIndex = text.indexOf("</interactive>");
            if (endIndex == -1) {
            	Synergy.getLogger().warning("Closing tag </interactive> is not found.");
                return;
            }
            String firstPart = text.substring(0, startIndex);
            String interactivePart = text.substring(startIndex + "<interactive>".length(), endIndex);
            String lastPart = text.substring(endIndex + "</interactive>".length());

            if (!firstPart.isEmpty()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("text", firstPart);
                inheritProperties(object, obj);
                dividedExtra.add(obj);
            }

            JsonObject interactiveObject = new JsonObject();

            Pattern pattern = Pattern.compile("<url>(.*?)</url>");
            Matcher matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
                JsonObject urlEvent = new JsonObject();
            	urlEvent.addProperty("action", "open_url");
            	urlEvent.addProperty("value", matcher.group(1));
            	interactiveObject.add("clickEvent", urlEvent);
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }

            pattern = Pattern.compile("<sound>(.*?)</sound>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
            	JsonObject soundEvent = new JsonObject();
            	soundEvent.addProperty("sound", matcher.group(1));
            	interactiveObject.add("soundEvent", soundEvent);
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }

            pattern = Pattern.compile("<hover>(.*?)</hover>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
                JsonObject hoverEvent = new JsonObject();
                hoverEvent.addProperty("action", "show_text");
                hoverEvent.addProperty("value", Color.processLegacyColors(matcher.group(1).replace("%nl%", System.lineSeparator()), "default"));
                interactiveObject.add("hoverEvent", hoverEvent);
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }

            pattern = Pattern.compile("<command>(.*?)</command>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
                JsonObject commandEvent = new JsonObject();
            	commandEvent.addProperty("action", "run_command");
                commandEvent.addProperty("value", matcher.group(1));
                interactiveObject.add("clickEvent", commandEvent);
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }

            pattern = Pattern.compile("<suggest>(.*?)</suggest>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
                JsonObject suggestEvent = new JsonObject();
                suggestEvent.addProperty("action", "suggest_command");
                suggestEvent.addProperty("value", matcher.group(1));
                interactiveObject.add("clickEvent", suggestEvent);
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }

            pattern = Pattern.compile("<copy>(.*?)</copy>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
                JsonObject copyEvent = new JsonObject();
                copyEvent.addProperty("action", "copy_to_clipboard");
                copyEvent.addProperty("value", matcher.group(1));
                interactiveObject.add("clickEvent", copyEvent);
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }

        	JsonObject titleEvent = new JsonObject();
            pattern = Pattern.compile("<title>(.*?)</title>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
            	titleEvent.addProperty("title", matcher.group(1));
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }
            pattern = Pattern.compile("<subtitle>(.*?)</subtitle>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
            	titleEvent.addProperty("subtitle", matcher.group(1));
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }
        	if (!titleEvent.entrySet().isEmpty()) {
            	titleEvent.addProperty("duration", 20*5);
        		interactiveObject.add("titleEvent", titleEvent);
        	}
        	
            pattern = Pattern.compile("<toast>(.*?)</toast>");
            matcher = pattern.matcher(interactivePart);
            if (matcher.find()) {
            	JsonObject toastEvent = new JsonObject();
            	toastEvent.addProperty("text", matcher.group(1));
            	interactiveObject.add("toastEvent", toastEvent);
                interactivePart = interactivePart.replaceAll(pattern.pattern(), "");
            }
        	
            interactiveObject.addProperty("text", interactivePart);
            inheritProperties(object, interactiveObject);

            dividedExtra.add(interactiveObject);

            if (!lastPart.isEmpty()) {
                JsonObject remains = new JsonObject();
                remains.addProperty("text", lastPart);
                inheritProperties(object, remains);
                splitInteractiveText(remains, dividedExtra);
            }
        }
    }

    private static void inheritProperties(JsonObject source, JsonObject target) {
        for (String key : source.keySet()) {
            if (!key.equals("text")) {
                target.add(key, source.get(key));
            }
        }
    }
}