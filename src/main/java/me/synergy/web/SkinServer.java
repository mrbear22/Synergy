package me.synergy.web;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import me.synergy.integrations.MojangAPI;
import me.synergy.integrations.SkinRestorerAPI;

public class SkinServer {

    private static final long CACHE_TIMEOUT = 10 * 60 * 1000; // 10 minutes
    private static final String CACHE_DIR = "plugins/Synergy/cache";
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // Shutdown hook для коректного закриття ExecutorService
    static {
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            
            Files.createDirectories(cacheDir.resolve("heads"));
            Files.createDirectories(cacheDir.resolve("skins"));
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdown();
            }));
            
        } catch (IOException e) {
            System.err.println("Помилка створення директорії кешу: " + e.getMessage());
        }
    }
    
    public static void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService не завершився коректно");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    enum Format {
        HEAD("heads"), SKIN("skins");
        
        private final String directory;
        
        Format(String directory) {
            this.directory = directory;
        }
        
        public String getDirectory() {
            return directory;
        }
    }

    static abstract class AbstractHandler implements HttpHandler {
        protected abstract Format getFormat();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (executorService.isShutdown()) {
                exchange.sendResponseHeaders(503, 0);
                exchange.close();
                return;
            }
            
            String[] segments = exchange.getRequestURI().getPath().split("/");

            if (segments.length >= 3 && !segments[2].isEmpty()) {
                UUID uuid = segments[2].length() == 36 ? UUID.fromString(segments[2]) : null;
                String name = segments[2].length() != 36 ? segments[2] : null;
                String cacheKey = (uuid != null) ? uuid.toString() : name;

                // НЕ відправляємо headers тут! Відправимо в async task
                executorService.submit(() -> {
                    HttpURLConnection connection = null;
                    InputStream skinStream = null;
                    InputStream defaultStream = null;
                    
                    try {
                        BufferedImage cachedImage = getCachedImage(cacheKey, getFormat());
                        BufferedImage processedImage;
                        
                        if (cachedImage != null) {
                            processedImage = cachedImage;
                        } else {
                            String skinUrl = getSkinUrl(uuid, name);
                            
                            if (skinUrl != null) {
                                connection = (HttpURLConnection) new URL(skinUrl).openConnection();
                                connection.setRequestMethod("GET");
                                connection.setConnectTimeout(5000);
                                connection.setReadTimeout(10000);
                                
                                if (connection.getResponseCode() == 200) {
                                    skinStream = connection.getInputStream();
                                    BufferedImage skinImage = ImageIO.read(skinStream);
                                    if (skinImage != null) {
                                        processedImage = processImage(skinImage);
                                    } else {
                                        defaultStream = getDefaultSkin();
                                        processedImage = processImage(ImageIO.read(defaultStream));
                                    }
                                } else {
                                    defaultStream = getDefaultSkin();
                                    processedImage = processImage(ImageIO.read(defaultStream));
                                }
                            } else {
                                defaultStream = getDefaultSkin();
                                processedImage = processImage(ImageIO.read(defaultStream));
                            }
                            
                            cacheImage(cacheKey, processedImage, getFormat());
                        }
                        
                        // Відправляємо headers і дані тут, в async task
                        exchange.getResponseHeaders().set("Content-Type", "image/png");
                        exchange.getResponseHeaders().set("Cache-Control", "max-age=600");
                        exchange.sendResponseHeaders(200, 0);
                        sendImageResponse(exchange, processedImage);
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        try {
                            InputStream errorDefault = getDefaultSkin();
                            try {
                                BufferedImage defaultImage = processImage(ImageIO.read(errorDefault));
                                exchange.getResponseHeaders().set("Content-Type", "image/png");
                                exchange.sendResponseHeaders(200, 0);
                                sendImageResponse(exchange, defaultImage);
                            } finally {
                                if (errorDefault != null) {
                                    try { errorDefault.close(); } catch (IOException ignored) {}
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            try {
                                exchange.sendResponseHeaders(500, 0);
                            } catch (IOException ignored) {}
                        }
                    } finally {
                        if (skinStream != null) {
                            try { skinStream.close(); } catch (IOException ignored) {}
                        }
                        if (defaultStream != null) {
                            try { defaultStream.close(); } catch (IOException ignored) {}
                        }
                        if (connection != null) {
                            connection.disconnect();
                        }
                        try {
                            exchange.close();
                        } catch (Exception ignored) {}
                    }
                });
            } else {
                // Для невалідних запитів відразу відповідаємо
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
            }
        }
        
        private void sendImageResponse(HttpExchange exchange, BufferedImage image) throws IOException {
            try (OutputStream os = exchange.getResponseBody()) {
                ImageIO.write(image, "png", os);
            }
        }

        protected BufferedImage processImage(BufferedImage image) {
            return image;
        }

        protected BufferedImage getCachedImage(String cacheKey, Format format) {
            if (cacheKey == null) return null;
            
            try {
                Path cacheFile = getCacheFilePath(cacheKey, format);
                
                if (!Files.exists(cacheFile)) {
                    return null;
                }
                
                long fileTime = Files.getLastModifiedTime(cacheFile).toMillis();
                long currentTime = System.currentTimeMillis();
                
                if ((currentTime - fileTime) > CACHE_TIMEOUT) {
                    Files.deleteIfExists(cacheFile);
                    return null;
                }
                
                return ImageIO.read(cacheFile.toFile());
                
            } catch (IOException e) {
                System.err.println("Помилка читання кешу для " + cacheKey + ": " + e.getMessage());
                return null;
            }
        }

        protected void cacheImage(String cacheKey, BufferedImage image, Format format) {
            if (cacheKey == null || image == null) return;
            
            try {
                Path cacheFile = getCacheFilePath(cacheKey, format);
                Files.createDirectories(cacheFile.getParent());
                ImageIO.write(image, "png", cacheFile.toFile());
                
            } catch (IOException e) {
                System.err.println("Помилка збереження кешу для " + cacheKey + ": " + e.getMessage());
            }
        }
        
        private Path getCacheFilePath(String cacheKey, Format format) {
            String safeKey = cacheKey.replaceAll("[^a-zA-Z0-9-_]", "_");
            return Paths.get(CACHE_DIR, format.getDirectory(), safeKey + ".png");
        }

        protected InputStream getDefaultSkin() {
            return getClass().getResourceAsStream("/steve.png");
        }
    }

    static class SkinHandler extends AbstractHandler {
        @Override
        protected Format getFormat() {
            return Format.SKIN;
        }
    }

    static class HeadHandler extends AbstractHandler {
        @Override
        protected Format getFormat() {
            return Format.HEAD;
        }

        @Override
        protected BufferedImage processImage(BufferedImage image) {
            BufferedImage combined = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = combined.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2d.drawImage(image.getSubimage(8, 8, 8, 8), 0, 0, 128, 128, null);
                g2d.drawImage(image.getSubimage(40, 8, 8, 8), 0, 0, 128, 128, null);
            } finally {
                g2d.dispose();
            }
            return combined;
        }
    }

    private static String getSkinUrl(UUID uuid, String name) {
        String skin = SkinRestorerAPI.getSkinTextureURL(uuid, name);
        if (skin != null) {
            return skin;
        }
        return MojangAPI.getSkinTextureURL(uuid, name);
    }
    
    public static void cleanupCache() {
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDir)) return;
            
            long currentTime = System.currentTimeMillis();
            
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".png"))
                .forEach(path -> {
                    try {
                        long fileTime = Files.getLastModifiedTime(path).toMillis();
                        if ((currentTime - fileTime) > CACHE_TIMEOUT) {
                            Files.deleteIfExists(path);
                        }
                    } catch (IOException e) {
                        System.err.println("Помилка видалення застарілого файлу кешу: " + e.getMessage());
                    }
                });
                
        } catch (IOException e) {
            System.err.println("Помилка очищення кешу: " + e.getMessage());
        }
    }
}