package me.synergy.web;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import me.synergy.brains.Synergy;
import me.synergy.integrations.MojangAPI;
import me.synergy.integrations.SkinRestorerAPI;
import me.synergy.modules.Http;

public class SkinServer {

    private static final long CACHE_TIMEOUT = 10 * 60 * 1000;
    private static final Path CACHE_DIR = Synergy.getDataFolder().toPath().resolve("cache");

    abstract static class ImageHandler implements HttpHandler {

        abstract String directory();

        BufferedImage process(BufferedImage skin) {
            return skin;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String[] segments = exchange.getRequestURI().getPath().split("/");
                if (segments.length < 3 || segments[2].isEmpty()) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                String key = segments[2];
                Path file = CACHE_DIR.resolve(directory()).resolve(key.replaceAll("[^a-zA-Z0-9-_]", "_") + ".png");
                BufferedImage image = cached(file);
                if (image == null) {
                    image = process(download(key));
                    Files.createDirectories(file.getParent());
                    ImageIO.write(image, "png", file.toFile());
                }
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.getResponseHeaders().set("Cache-Control", "max-age=600");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream out = exchange.getResponseBody()) {
                    ImageIO.write(image, "png", out);
                }
            } catch (IOException | RuntimeException e) {
                Synergy.getLogger().warning("Skin server: " + e.getMessage());
            }
        }

        private static BufferedImage cached(Path file) throws IOException {
            if (!Files.exists(file)) return null;
            if (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() > CACHE_TIMEOUT) {
                Files.deleteIfExists(file);
                return null;
            }
            return ImageIO.read(file.toFile());
        }

        private static BufferedImage download(String key) throws IOException {
            UUID uuid = key.length() == 36 ? UUID.fromString(key) : null;
            String name = uuid == null ? key : null;
            String url = SkinRestorerAPI.getSkinTextureURL(uuid, name);
            if (url == null) url = MojangAPI.getSkinTextureURL(uuid, name);
            Http.Response response = url == null ? null : Http.get(url).send();
            BufferedImage skin = response != null && response.ok() ? ImageIO.read(new ByteArrayInputStream(response.body())) : null;
            if (skin != null) return skin;
            try (InputStream steve = SkinServer.class.getResourceAsStream("/steve.png")) {
                return ImageIO.read(steve);
            }
        }
    }

    static class SkinHandler extends ImageHandler {
        @Override
        String directory() {
            return "skins";
        }
    }

    static class HeadHandler extends ImageHandler {
        @Override
        String directory() {
            return "heads";
        }

        @Override
        BufferedImage process(BufferedImage skin) {
            BufferedImage head = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = head.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, 128, 128, null);
            g.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, 128, 128, null);
            g.dispose();
            return head;
        }
    }
}
