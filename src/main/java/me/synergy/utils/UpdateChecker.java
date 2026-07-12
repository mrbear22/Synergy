package me.synergy.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;

public class UpdateChecker {

    public enum Platform {
        SPIGOT,
        BUNGEECORD,
        VELOCITY
    }

    private final String repoOwner;
    private final String repoName;
    private final Platform platform;

    public UpdateChecker(String repoOwner, String repoName, Platform platform) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.platform = platform;
    }

    private String getCurrentVersion() {
        return switch (platform) {
            case SPIGOT     -> Synergy.getSpigot().getPluginMeta().getVersion();
            case BUNGEECORD -> Synergy.getBungee().getDescription().getVersion();
            case VELOCITY -> Velocity.getProxy().getPluginManager()
						            .getPlugin("synergy")
						            .flatMap(p -> p.getDescription().getVersion())
						            .orElse("unknown");
        };
    }

    public void checkForUpdates() {
        new Thread(() -> {
            try {
                URI uri = new URI("https", "api.github.com", "/repos/" + repoOwner + "/" + repoName + "/releases/latest", null);
                URL url = uri.toURL();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                String latestVersion = jsonResponse.get("tag_name").getAsString();
                String currentVersion = getCurrentVersion();
                String downloadUrl = jsonResponse.getAsJsonArray("assets").size() > 0
                    ? jsonResponse.getAsJsonArray("assets").get(0).getAsJsonObject().get("browser_download_url").getAsString()
                    : jsonResponse.get("html_url").getAsString();

                if (!latestVersion.equalsIgnoreCase(currentVersion)) {
                    Synergy.getLogger().warning("New update available: " + latestVersion + "! Current version: " + currentVersion);
                    Synergy.getLogger().warning("Download it here: " + downloadUrl);
                } else {
                    Synergy.getLogger().info("Synergy is updated to the latest version!");
                }
            } catch (Exception e) {
                Synergy.getLogger().error("Unable to check for updates: " + e.getMessage());
            }
        }).start();
    }
}