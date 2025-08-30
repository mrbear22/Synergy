package me.synergy.integrations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.synergy.brains.Synergy;

public class DeepLTranslator {
    
    private static final String DEEPL_API_URL = "https://api-free.deepl.com/v2/translate";
    private static final String DEEPL_API_URL_PRO = "https://api.deepl.com/v2/translate";
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList(
        "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hu", "id", 
        "it", "ja", "ko", "lt", "lv", "nb", "nl", "pl", "pt", "ro", "ru", "sk", 
        "sl", "sv", "tr", "uk", "zh"
    );
    
    private final String apiKey;
    private final boolean isProAccount;
    @SuppressWarnings("unused")
	private final int requestDelay;
    
    public DeepLTranslator() {
        this.apiKey = Synergy.getConfig().getString("deepl.api-key");
        this.isProAccount = Synergy.getConfig().getBoolean("deepl.pro-account", false);
        this.requestDelay = Synergy.getConfig().getInt("deepl.request-delay-ms", 200);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("DeepL API key not found in config! Please set 'deepl.api-key' in config.yml");
        }
    }
    
    /**
     * Translate text from source language to target language
     */
    public String translate(String text, String sourceLang, String targetLang) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        String apiUrl = isProAccount ? DEEPL_API_URL_PRO : DEEPL_API_URL;
        
        // Prepare request body
        String requestBody = "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                           "&source_lang=" + sourceLang.toUpperCase() +
                           "&target_lang=" + targetLang.toUpperCase();
        
        // Create connection
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            // Configure request
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "DeepL-Auth-Key " + apiKey);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "Synergy-Plugin/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(30000);    // 30 seconds
            
            // Send request
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                // Success - read response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    return parseTranslationResponse(response.toString());
                }
            } else {
                // Error - read error response
                String errorMessage = readErrorResponse(connection);
                handleApiError(responseCode, errorMessage);
                return null;
            }
            
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Parse JSON response from DeepL API
     */
    private String parseTranslationResponse(String jsonResponse) {
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray translations = jsonObject.getAsJsonArray("translations");
            
            if (translations != null && translations.size() > 0) {
                JsonObject firstTranslation = translations.get(0).getAsJsonObject();
                return firstTranslation.get("text").getAsString();
            }
            
            Synergy.getLogger().warning("No translations found in DeepL response");
            return null;
            
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to parse DeepL response: " + jsonResponse);
            return null;
        }
    }
    
    /**
     * Read error response from connection
     */
    private String readErrorResponse(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
            
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line);
            }
            return error.toString();
            
        } catch (IOException e) {
            return "Unable to read error response";
        }
    }
    
    /**
     * Handle API errors based on response code
     */
    private void handleApiError(int responseCode, String errorMessage) throws IOException {
        switch (responseCode) {
            case 400:
                throw new IOException("Bad request - Invalid parameters: " + errorMessage);
            case 401:
                throw new IOException("Unauthorized - Invalid API key: " + errorMessage);
            case 403:
                throw new IOException("Forbidden - API key lacks permission: " + errorMessage);
            case 404:
                throw new IOException("Not found - Invalid endpoint: " + errorMessage);
            case 413:
                throw new IOException("Request too large - Text too long: " + errorMessage);
            case 429:
                throw new IOException("Too many requests - Rate limit exceeded: " + errorMessage);
            case 456:
                throw new IOException("Quota exceeded - DeepL quota limit reached: " + errorMessage);
            case 500:
            case 502:
            case 503:
            case 504:
                throw new IOException("Server error - DeepL service temporarily unavailable: " + errorMessage);
            default:
                throw new IOException("Unexpected error (HTTP " + responseCode + "): " + errorMessage);
        }
    }
    
    /**
     * Check if language is supported by DeepL
     */
    public boolean isLanguageSupported(String languageCode) {
        return SUPPORTED_LANGUAGES.contains(languageCode.toLowerCase());
    }
    
    /**
     * Get list of supported languages
     */
    public List<String> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }
    
    /**
     * Translate multiple texts in batch (more efficient for large amounts)
     * Note: This method should be called from async context with proper delays between calls
     */
    public String[] translateBatch(String[] texts, String sourceLang, String targetLang) 
            throws IOException {
        
        if (texts == null || texts.length == 0) {
            return texts;
        }
        
        // For now, translate one by one. DeepL supports batch, but it's more complex
        String[] results = new String[texts.length];
        
        for (int i = 0; i < texts.length; i++) {
            results[i] = translate(texts[i], sourceLang, targetLang);
            
            // Progress logging for large batches
            if (texts.length > 10 && (i + 1) % 5 == 0) {
                Synergy.getLogger().info("Batch translation progress: " + (i + 1) + "/" + texts.length);
            }
        }
        
        return results;
    }
    
    /**
     * Test API connectivity and key validity
     */
    public boolean testConnection() {
        try {
            String result = translate("Hello", "en", "es");
            return result != null && !result.trim().isEmpty();
        } catch (Exception e) {
            Synergy.getLogger().error("DeepL connection test failed " + e);
            return false;
        }
    }
}