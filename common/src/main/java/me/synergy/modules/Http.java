package me.synergy.modules;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .executor(Tasks.executor())
        .build();

    public record Response(int status, byte[] body) {
        public boolean ok() { return status / 100 == 2; }
        public String text() { return new String(body, StandardCharsets.UTF_8); }
        public JsonObject json() { return JsonParser.parseString(text()).getAsJsonObject(); }
    }

    private final HttpRequest.Builder request;
    private String method = "GET";
    private HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.noBody();

    private Http(String url) {
        request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", "Synergy");
    }

    public static Http get(String url) {
        return new Http(url);
    }

    public static Http post(String url) {
        return method("POST", url);
    }

    public static Http delete(String url) {
        return method("DELETE", url);
    }

    private static Http method(String method, String url) {
        Http http = new Http(url);
        http.method = method;
        return http;
    }

    public Http header(String name, String value) {
        request.setHeader(name, value);
        return this;
    }

    public Http json(JsonElement json) {
        return body("application/json", json.toString());
    }

    public Http form(String form) {
        return body("application/x-www-form-urlencoded", form);
    }

    private Http body(String type, String content) {
        header("Content-Type", type);
        body = HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8);
        return this;
    }

    public Response send() throws IOException {
        try {
            HttpResponse<byte[]> response = CLIENT.send(request.method(method, body).build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    public CompletableFuture<Response> async() {
        return CLIENT.sendAsync(request.method(method, body).build(), HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(response -> new Response(response.statusCode(), response.body()));
    }
}
