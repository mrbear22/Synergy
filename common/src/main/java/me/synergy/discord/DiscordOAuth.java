package me.synergy.discord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Http;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;
import me.synergy.web.WebServer;

public class DiscordOAuth {

    private static final String API = "https://discord.com/api";

    public static boolean isEnabled() {
        String clientId = Config.getString("discord.oauth.client-id");
        String clientSecret = Config.getString("discord.oauth.client-secret");
        return WebServer.isRunning()
            && clientId != null && !clientId.isEmpty()
            && clientSecret != null && !clientSecret.isEmpty();
    }

    public static String getRedirectUri() {
        return WebServer.getFullAddress() + "/discord";
    }

    public static String createAuthorizationUrl(UUID uuid) {
        String state = UUID.randomUUID().toString().replace("-", "");
        new BreadMaker(uuid).setData("discord-oauth", state, Duration.ofMinutes(10));

        return WebServer.getFullAddress() + "/discord?state=" + state;
    }

    private static String buildDiscordUrl(String state) {
        return API + "/oauth2/authorize"
             + "?client_id=" + Config.getString("discord.oauth.client-id")
             + "&redirect_uri=" + encode(getRedirectUri())
             + "&response_type=code"
             + "&scope=identify"
             + "&prompt=consent"
             + "&state=" + state;
    }

    public static class CallbackHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String code = query.get("code");
            String state = query.get("state");

            if (code == null && state != null) {
                if (Synergy.findUserUUID("discord-oauth", state) == null) {
                    respond(exchange, 403, render(LocaleBuilder.of("discord.link.oauth.expired"), null));
                    return;
                }
                redirect(exchange, buildDiscordUrl(state));
                return;
            }

            if (code == null || state == null) {
                respond(exchange, 400, render(LocaleBuilder.of("discord.link.oauth.expired"), null));
                return;
            }

            UUID uuid = Synergy.findUserUUID("discord-oauth", state);
            if (uuid == null) {
                respond(exchange, 403, render(LocaleBuilder.of("discord.link.oauth.expired"), null));
                return;
            }

            BreadMaker bread = new BreadMaker(uuid);
            bread.setData("discord-oauth", null);

            try {
                String discordId = requestUserId(requestAccessToken(code));
                Synergy.getLogger().info("Discord OAuth: " + uuid + " -> " + discordId);

                String existing = Discord.getDiscordIdByUniqueId(uuid);
                if (existing != null) {
                    respond(exchange, 200, render(LocaleBuilder.of("discord.link.exists").placeholder("account", Discord.name(existing)), bread));
                    return;
                }

                UUID linkedTo = Discord.getUniqueIdByDiscordId(discordId);
                if (linkedTo != null) {
                    respond(exchange, 200, render(LocaleBuilder.of("discord.link.exists")
                            .placeholder("account", Synergy.getBread(linkedTo).getName()), bread));
                    return;
                }

                LinkCommand.link(uuid, discordId);

                redirect(exchange, Config.getString("discord.invite-link"));
            } catch (Exception e) {
                Synergy.getLogger().warning("Discord OAuth failed: " + e.getMessage());
                respond(exchange, 502, render(LocaleBuilder.of("discord.service.unavailable"), bread));
            }
        }
    }

    private static String requestAccessToken(String code) throws IOException {
        return call(Http.post(API + "/oauth2/token").form("client_id=" + encode(Config.getString("discord.oauth.client-id"))
            + "&client_secret=" + encode(Config.getString("discord.oauth.client-secret"))
            + "&grant_type=authorization_code&code=" + encode(code) + "&redirect_uri=" + encode(getRedirectUri()))).get("access_token").getAsString();
    }

    private static String requestUserId(String accessToken) throws IOException {
        return call(Http.get(API + "/users/@me").header("Authorization", "Bearer " + accessToken)).get("id").getAsString();
    }

    private static JsonObject call(Http request) throws IOException {
        Http.Response response = request.send();
        if (!response.ok()) throw new IOException("Discord API " + response.status() + ": " + response.text());
        return response.json();
    }

    private static String render(LocaleBuilder locale, BreadMaker bread) {
        return locale.stripped(bread != null ? bread.getLanguage() : Translation.getDefaultLanguage()).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int index = pair.indexOf('=');
            if (index > 0) {
                result.put(pair.substring(0, index),
                    URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        byte[] page = ("<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                     + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                     + "<title>Discord</title></head>"
                     + "<body style=\"font-family:sans-serif;display:flex;align-items:center;"
                     + "justify-content:center;height:100vh;margin:0;text-align:center\">"
                     + "<h2>" + message + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(status, page.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(page);
        }
    }
}