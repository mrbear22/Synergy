package me.synergy.modules;

import com.google.gson.JsonObject;

import me.synergy.brains.Synergy;

public final class UpdateChecker {

    private UpdateChecker() {}

    public static void check(String owner, String repo) {
        Http.get("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest").async().thenAccept(response -> {
            if (!response.ok()) return;
            JsonObject release = response.json();
            String latest = release.get("tag_name").getAsString(), current = Synergy.platform().version();
            if (!isNewer(latest, current)) return;
            var assets = release.getAsJsonArray("assets");
            String url = assets.isEmpty() ? release.get("html_url").getAsString() : assets.get(0).getAsJsonObject().get("browser_download_url").getAsString();
            Synergy.getLogger().warning("Synergy " + latest + " is available (current " + current + "): " + url);
        });
    }

    static boolean isNewer(String latest, String current) {
        String[] a = latest.replaceAll("[^0-9.]", "").split("\\."), b = current.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0, y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }
}
