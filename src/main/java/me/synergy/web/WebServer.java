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
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import me.synergy.brains.Synergy;

public class WebServer {
    
    // JVM system properties для HttpServer таймаутів
    static {
        System.setProperty("sun.net.httpserver.maxReqTime", "30");
        System.setProperty("sun.net.httpserver.maxRspTime", "30");
        System.setProperty("sun.net.httpserver.nodelay", "true");
    }

    private static HttpServer server;
    private static ThreadPoolExecutor executor;
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
            // Створюємо сервер з backlog 0 (використовує системне значення за замовчуванням)
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Створюємо ThreadPool з обмеженою кількістю потоків
            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
            server.setExecutor(executor);
            
            server.createContext("/", new PublicHtmlHandler());
            server.createContext("/skin", new SkinServer.SkinHandler());
            server.createContext("/head", new SkinServer.HeadHandler());
            server.createContext("/status", new WebServerStatusHandler());
            server.createContext("/logs", new LogsHandler());
            server.createContext("/monobank", new MonobankHandler.WebhookHandler());
            
            server.start();
            isRunning = true;
            Synergy.getLogger().info("Web server successfully started on " + fullAddress);

            loadWebFiles();
        } catch (IOException e) {
            Synergy.getLogger().warning("Failed to start web server: " + e.getMessage());
            isRunning = false;
        }
    }

    public void shutdown() {
        if (server != null) {
            isRunning = false;
            
            // Graceful shutdown - даємо 5 секунд на завершення активних запитів
            server.stop(5);
            server = null;
            
            if (executor != null) {
                executor.shutdown();
                executor = null;
            }
            
            Synergy.getLogger().info("Web server stopped.");
        }
    }

    public void restart() {
        shutdown();
        start();
    }

    public static boolean isRunning() {
        return isRunning;
    }   
    
    public void monitorServer() {
        if (!isRunning) {
            Synergy.getLogger().info("Web server is not running, attempting to restart...");
            restart();
            return;
        }
        
        try {
            URL url = new URL(fullAddress + "/status");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            connection.disconnect(); // КРИТИЧНО: закриваємо з'єднання
            
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
                if (inputStream != null) {
                    File indexFile = new File(webFolder, "index.html");
                    Files.copy(inputStream, indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Synergy.getLogger().info("Copied index.html to the 'public_html' folder.");
                }
            } catch (IOException e) {
                Synergy.getLogger().warning("Failed to copy index.html to the 'public_html' folder!");
                e.printStackTrace();
            }
        }
    }

    public static String getFullAddress() {
        return fullAddress;
    }

    private class WebServerStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Швидкий статус з правильними заголовками
            String response = "ok";
            exchange.getResponseHeaders().set("Connection", "close"); // Закриваємо з'єднання
            exchange.sendResponseHeaders(200, response.length());
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private class PublicHtmlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            File webFolder = new File("public_html");
            
            // Якщо запит до кореня - повертаємо index.html
            if ("/".equals(path)) {
                path = "/index.html";
            }
            
            File requestedFile = new File(webFolder, path);

            if (requestedFile.exists() && !requestedFile.isDirectory()) {
                String contentType = getContentType(path);
                long fileSize = requestedFile.length();
                
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileSize));
                exchange.getResponseHeaders().set("Connection", "close"); // Закриваємо з'єднання
                exchange.sendResponseHeaders(200, fileSize);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(requestedFile.toPath(), os);
                }
            } else {
                String response = "404 Not Found";
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.sendResponseHeaders(404, response.length());
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }

        private String getContentType(String path) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".html")) return "text/html";
            if (lower.endsWith(".css")) return "text/css"; 
            if (lower.endsWith(".js")) return "application/javascript";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".gif")) return "image/gif";
            if (lower.endsWith(".ico")) return "image/x-icon";
            if (lower.endsWith(".zip")) return "application/zip";
            if (lower.endsWith(".woff2")) return "font/woff2";
            if (lower.endsWith(".woff")) return "font/woff";
            if (lower.endsWith(".ttf")) return "font/ttf";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            return "application/octet-stream";
        }
    }
}