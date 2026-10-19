package me.synergy.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import me.synergy.discord.DiscordOAuth;
import me.synergy.modules.Config;
import me.synergy.modules.Module;
import me.synergy.modules.Tasks;

public class WebServer implements Module {

    static {
        System.setProperty("sun.net.httpserver.maxReqTime", "30");
        System.setProperty("sun.net.httpserver.maxRspTime", "30");
        System.setProperty("sun.net.httpserver.nodelay", "true");
    }

    private static HttpServer server;

    @Override public Side side() { return Side.HUB; }
    @Override public String toggle() { return "web-server.enabled"; }
    @Override public String name() { return "Web server"; }

    @Override
    public void defaults() {
        Config.addDefault("web-server.enabled", false);
        Config.addDefault("web-server.domain", "example.com");
        Config.addDefault("web-server.port", 8153);
    }

    @Override
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(Config.getInt("web-server.port", 8153)), 0);
        server.setExecutor(Tasks.executor());
        server.createContext("/skin", new SkinServer.SkinHandler());
        server.createContext("/head", new SkinServer.HeadHandler());
        server.createContext("/monobank", new MonobankHandler.WebhookHandler());
        server.createContext("/discord", new DiscordOAuth.CallbackHandler());
        server.createContext("/status", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("ok".getBytes());
            }
        });
        server.start();
    }

    @Override
    public void stop() {
        if (server != null) server.stop(1);
        server = null;
    }

    public static boolean isRunning() {
        return server != null;
    }

    public static String getFullAddress() {
        return "http://" + Config.getString("web-server.domain") + ":" + Config.getInt("web-server.port", 8153);
    }
}
