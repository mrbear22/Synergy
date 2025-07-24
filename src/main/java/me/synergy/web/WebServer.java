package me.synergy.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import me.synergy.brains.Synergy;

public class WebServer {

    private static HttpServer server;
    private static int port = Synergy.getConfig().getInt("web-server.port");
    private static String serverAddress = Synergy.getConfig().getString("web-server.domain");
    private static String fullAddress = "http://" + serverAddress + ":" + port;
    private static boolean isRunning = false;
    public static final long MONITOR_INTERVAL_SECONDS = 60L;

    public void initialize() {
        if (!Synergy.getConfig().getBoolean("web-server.enabled")) {
            return;
        }
        start();

        if (Synergy.isRunningSpigot()) {
            Synergy.getSpigot().startSpigotMonitor();
        } else if (Synergy.isRunningBungee()) {
            Synergy.getBungee().startBungeeMonitor();
        }
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new PublicHtmlHandler());
            server.createContext("/skin", new SkinServer.SkinHandler());
            server.createContext("/head", new SkinServer.HeadHandler());
            server.createContext("/status", new WebServerStatusHandler());
            server.createContext("/logs", new LogsHandler()); // Використовуємо новий клас
            server.setExecutor(null);
            server.start();
            isRunning = true;
            Synergy.getLogger().info("Web server successfully started on " + fullAddress);

            loadResourcePackFolder();
            loadWebFiles();
        } catch (IOException e) {
            Synergy.getLogger().warning("Failed to start web server: " + e.getMessage());
            isRunning = false;
        }
    }

    public void shutdown() {
        if (server != null) {
            server.stop(0);
            isRunning = false;
            Synergy.getLogger().info("Web server stopped.");
        }
    }

    public void restart() {
        shutdown();
        start();
    }

    public void monitorServer() {
        if (!isRunning) {
            Synergy.getLogger().info("Web server is not running, attempting to restart...");
            restart();
            return;
        }
        try {
            @SuppressWarnings("deprecation")
            URL url = new URL(fullAddress + "/status");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Synergy.getLogger().warning("Web server returned non-OK response (" + responseCode + "), restarting...");
                restart();
            }
        } catch (IOException e) {
            Synergy.getLogger().warning("Failed to check web server status: " + e.getMessage() + ". Restarting...");
            restart();
        }
    }

    private void loadWebFiles() {
        File webFolder = new File("public_html");
        if (!webFolder.exists()) {
            boolean created = webFolder.mkdirs();
            if (!created) {
                Synergy.getLogger().warning("Failed to create the 'public_html' folder!");
                return;
            }
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("index.html")) {
                File indexFile = new File(webFolder, "index.html");
                Files.copy(inputStream, indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Synergy.getLogger().info("Copied index.html to the 'public_html' folder.");
            } catch (IOException e) {
                Synergy.getLogger().warning("Failed to copy index.html to the 'public_html' folder!");
                e.printStackTrace();
            }
        }
    }

    private void loadResourcePackFolder() {
        File webFolder = new File("resourcepack");
        if (!webFolder.exists()) {
            boolean created = webFolder.mkdirs();
            if (!created) {
                Synergy.getLogger().warning("Failed to create the 'resourcepack' folder!");
                return;
            }
        }
    }

    public static String getFullAddress() {
        return fullAddress;
    }

    private class WebServerStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "ok";
            exchange.sendResponseHeaders(200, response.length());
        }
    }

    private class PublicHtmlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            File webFolder = new File("public_html");
            File requestedFile = new File(webFolder, path);

            if (requestedFile.exists() && !requestedFile.isDirectory()) {
                String contentType = getContentType(path);
                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                Files.copy(requestedFile.toPath(), os);
                os.close();
            } else {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) {
                return "text/html";
            } else if (path.endsWith(".css")) {
                return "text/css";
            } else if (path.endsWith(".js")) {
                return "application/javascript";
            } else if (path.endsWith(".png")) {
                return "image/png";
            } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (path.endsWith(".woff2")) {
                return "font/woff2";
            } else if (path.endsWith(".woff")) {
                return "font/woff";
            } else if (path.endsWith(".ttf")) {
                return "font/ttf";
            } else if (path.endsWith(".svg")) {
                return "image/svg+xml";
            } else {
                return "application/octet-stream";
            }
        }
    }
}