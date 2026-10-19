package me.synergy.integrations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.synergy.brains.Synergy;
import me.synergy.modules.Http;
import me.synergy.objects.BreadMaker;

public final class MojangAPI {

    private MojangAPI() {}

    public static String getMojangUUIDbyUsername(String name) {
        if (name == null) return null;
        UUID uuid = Synergy.findUserUUID("name", name);
        BreadMaker bread = uuid != null ? Synergy.getBread(uuid) : null;
        String cached = bread != null ? bread.getData("mojangUUID").getAsString() : null;
        if (cached != null) return cached;
        JsonObject profile = json("https://api.mojang.com/users/profiles/minecraft/" + name);
        String id = profile != null && profile.has("id") ? profile.get("id").getAsString() : null;
        if (id != null && bread != null) bread.setData("mojangUUID", id);
        return id;
    }

    public static String getSkinTextureURL(UUID uuid, String name) {
        String id = getMojangUUIDbyUsername(name != null ? name : uuid != null ? Synergy.getBread(uuid).getName() : null);
        JsonObject profile = id == null ? null : json("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
        if (profile == null || profile.getAsJsonArray("properties").isEmpty()) return null;
        return getSkinTextureURLStringFromBase64Blob(profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString());
    }

    public static String getSkinTextureURLStringFromBase64Blob(String base64Blob) {
        try {
            JsonObject root = JsonParser.parseString(new String(Base64.getDecoder().decode(base64Blob), StandardCharsets.UTF_8)).getAsJsonObject();
            return root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject json(String url) {
        try {
            Http.Response response = Http.get(url).send();
            return response.ok() ? response.json() : null;
        } catch (Exception e) {
            Synergy.getLogger().warning("Mojang API request failed: " + e.getMessage());
            return null;
        }
    }
}
