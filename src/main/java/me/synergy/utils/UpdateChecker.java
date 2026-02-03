package me.synergy.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.synergy.brains.Synergy;

public class UpdateChecker {

    private final String repoOwner;
    private final String repoName;

    public UpdateChecker(String repoOwner, String repoName) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
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
                String currentVersion = Synergy.getSpigot().getPluginMeta().getVersion();
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
