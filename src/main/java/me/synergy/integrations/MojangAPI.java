package me.synergy.integrations;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Collectors;

public class MojangAPI {

    private static String readFromURL(String urlString) throws IOException {
        URL url = new URL(urlString);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return in.lines().collect(Collectors.joining());
        }
    }

    public static String getSkinTextureURLStringFromBase64Blob(String base64Blob) {
        try {
            String skinJSONString = new String(Base64.getDecoder().decode(base64Blob), StandardCharsets.UTF_8);
            JsonObject skinJSONRoot = JsonParser.parseString(skinJSONString).getAsJsonObject();
            JsonObject skinJSONTexturesDict = skinJSONRoot.getAsJsonObject("textures");
            JsonObject skinJSONSkinElement = skinJSONTexturesDict.getAsJsonObject("SKIN");
            return skinJSONSkinElement.get("url").getAsString();
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getMojangUUIDbyUsername(String name) {
        if (name == null) {
            return null;
        }

        UUID uuid = Synergy.findUserUUID("name", name);
        BreadMaker bread = (uuid != null) ? Synergy.getBread(uuid) : null;

        String mojangUuid = (bread != null) ? bread.getData("mojangUUID").getAsString() : null;

        if (mojangUuid != null) {
            return mojangUuid;
        }

        try {
            String userProfileAPIURL = readFromURL("https://api.mojang.com/users/profiles/minecraft/" + name);
            JsonObject userProfileJSON = JsonParser.parseString(userProfileAPIURL).getAsJsonObject();
            mojangUuid = userProfileJSON.get("id").getAsString();

            if (bread != null) {
                bread.setData("mojangUUID", mojangUuid);
            }

        } catch (JsonSyntaxException | IOException e) { }

        return mojangUuid;
    }

    public static String getSkinTextureURL(UUID uuid, String name) {
        try {
            if (uuid != null && name == null) {
                name = Synergy.getBread(uuid).getName();
            }
            
            if (name == null) {
                return null;
            }

            String mojangID = getMojangUUIDbyUsername(name);
            if (mojangID == null) {
                return null;
            }

            String mojangSessionAPIResponse = readFromURL("https://sessionserver.mojang.com/session/minecraft/profile/" + mojangID);
            JsonObject mojangJSONRoot = JsonParser.parseString(mojangSessionAPIResponse).getAsJsonObject();
            JsonArray propertiesArray = mojangJSONRoot.getAsJsonArray("properties");

            if (propertiesArray.size() == 0) {
                return null;
            }

            JsonObject skinProperty = propertiesArray.get(0).getAsJsonObject();
            String base64Blob = skinProperty.get("value").getAsString();

            return getSkinTextureURLStringFromBase64Blob(base64Blob);
        } catch (JsonSyntaxException | IOException e) {
            return null;
        }
    }
}

