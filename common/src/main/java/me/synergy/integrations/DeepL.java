package me.synergy.integrations;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import me.synergy.modules.Config;
import me.synergy.modules.Http;

public final class DeepL {

    public static final List<String> LANGUAGES = List.of("bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hu", "id", "it", "ja", "ko", "lt", "lv", "nb", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "zh");

    private DeepL() {}

    public static String translate(String text, String source, String target) throws IOException {
        if (text.isBlank()) return text;
        Http.Response response = Http.post(Config.getBoolean("deepl.pro-account") ? "https://api.deepl.com/v2/translate" : "https://api-free.deepl.com/v2/translate")
            .header("Authorization", "DeepL-Auth-Key " + Config.getString("deepl.api-key"))
            .form("text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&source_lang=" + source.toUpperCase() + "&target_lang=" + target.toUpperCase())
            .send();
        if (!response.ok()) throw new IOException("DeepL HTTP " + response.status() + ": " + response.text());
        return response.json().getAsJsonArray("translations").get(0).getAsJsonObject().get("text").getAsString();
    }
}
