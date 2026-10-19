package me.synergy.modules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;

public final class Messaging {

    public static final String PROXY = "@proxy";

    private record Pending(byte[] data, long created, long[] sent) {}

    private static final Map<String, Map<String, Pending>> pending = new ConcurrentHashMap<>();
    private static final Deque<byte[]> outbox = new ArrayDeque<>();
    private static final Set<String> seen = Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 2048;
        }
    }));
    private static final Map<String, CompletableFuture<SynergyEvent>> requests = new ConcurrentHashMap<>();
    private static volatile boolean trace, linked, warned;

    private Messaging() {}

    public static void start() {
        Config.addDefault("messaging.enabled", Synergy.isProxy());
        Config.addDefault("messaging.servername", Synergy.isProxy() ? "Proxy" : "SomeFabulousServer");
        Config.addDefault("messaging.token", Synergy.isProxy() ? Config.secret(50) : "Copy messaging.token from the proxy's config.yml");
        Config.addDefault("messaging.queue-ttl-seconds", 600);
        Config.addDefault("messaging.not-queued", new String[] {"global-chat", "broadcast", "system-chat", "discord-chat", "twitch-chat", "tiktok-chat", "chat-lock-sync"});
        if (Synergy.isProxy()) Tasks.every(Messaging::retry, Duration.ofSeconds(15));
        Synergy.getEventManager().registerEvents(Messaging.class);
    }

    @SynergyHandler("synergy-hello")
    public static void onHello(SynergyEvent event) {
        if (Synergy.isProxy()) event.reply().setOption("server", event.getFrom()).send();
    }

    private static void hello() {
        if (linked || !isEnabled() || Synergy.isProxy()) return;
        Synergy.event("synergy-hello").toProxy().request().whenComplete((reply, error) -> {
            if (reply == null) {
                if (!warned) Synergy.getLogger().warning("No answer from the proxy: install Synergy on the proxy, copy messaging.token from its config.yml, or set messaging.enabled: false for a standalone server");
                warned = true;
                return;
            }
            linked = true;
            String name = reply.getOption("server").getAsString();
            if (name.equals(Synergy.getServerName())) return;
            Synergy.getLogger().info("messaging.servername set to '" + name + "' as the proxy calls this server");
            Config.set("messaging.servername", name);
            Config.save();
        });
    }

    public static boolean isEnabled() {
        return Config.getBoolean("messaging.enabled", false);
    }

    public static boolean toggleTrace() {
        return trace = !trace;
    }

    public static void send(SynergyEvent event) {
        if (!isEnabled()) {
            deliver(event);
            return;
        }
        byte[] data = encode(event.toJson());
        log("->", event);
        if (Synergy.isProxy()) {
            route(event);
        } else if (!Synergy.platform().send(null, data)) {
            synchronized (outbox) {
                outbox.add(data);
            }
        }
    }

    public static CompletableFuture<SynergyEvent> request(SynergyEvent event, Duration timeout) {
        CompletableFuture<SynergyEvent> future = new CompletableFuture<SynergyEvent>().orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        requests.put(event.getId(), future);
        future.whenComplete((r, e) -> requests.remove(event.getId()));
        send(event);
        return future;
    }

    public static void receive(byte[] data, String server) {
        JsonObject json = decode(data);
        if (json == null) return;
        if (json.has("ack")) {
            Map<String, Pending> queue = pending.get(server);
            if (queue != null) queue.remove(json.get("ack").getAsString());
            return;
        }
        SynergyEvent event = SynergyEvent.fromJson(json);
        if (Synergy.isProxy()) event.setFrom(server);
        else Synergy.platform().send(null, encodeAck(event.getId()));
        if (!seen.add(event.getId())) return;
        log("<-", event);
        if (Synergy.isProxy()) route(event);
        else deliver(event);
    }

    public static void flush(String server) {
        if (server != null) {
            Map<String, Pending> queue = pending.get(server);
            if (queue != null) queue.values().forEach(p -> transmit(server, p));
            return;
        }
        synchronized (outbox) {
            while (!outbox.isEmpty() && Synergy.platform().send(null, outbox.peek())) outbox.poll();
        }
        hello();
    }

    private static void route(SynergyEvent event) {
        String target = event.getTarget();
        if (event.isToPlayer() && (target = Synergy.platform().serverOf(event.getPlayerUniqueId())) == null) return;
        String self = Synergy.getServerName();
        if (PROXY.equals(target)) target = self;
        if (target == null ? !self.equals(event.getFrom()) : target.equals(self)) deliver(event);
        if (target != null && target.equals(self)) return;
        byte[] data = encode(event.toJson());
        long now = System.currentTimeMillis();
        Map<String, List<String>> servers = Synergy.platform().playersByServer();
        boolean queued = !Config.getStringList("messaging.not-queued").contains(event.getIdentifier());
        for (String server : target != null ? List.of(target) : servers.keySet()) {
            if (!queued && servers.getOrDefault(server, List.of()).isEmpty()) continue;
            Pending p = new Pending(data, now, new long[1]);
            pending.computeIfAbsent(server, s -> new ConcurrentHashMap<>()).put(event.getId(), p);
            transmit(server, p);
        }
    }

    private static void transmit(String server, Pending p) {
        if (Synergy.platform().send(server, p.data())) p.sent()[0] = System.currentTimeMillis();
    }

    private static void retry() {
        long now = System.currentTimeMillis(), ttl = Config.getInt("messaging.queue-ttl-seconds", 600) * 1000L;
        pending.forEach((server, queue) -> queue.values().removeIf(p -> {
            if (now - p.created() > ttl) return true;
            if (now - p.sent()[0] > 10_000) transmit(server, p);
            return false;
        }));
    }

    private static void deliver(SynergyEvent event) {
        if (event.getReplyTo() != null) {
            CompletableFuture<SynergyEvent> future = requests.get(event.getReplyTo());
            if (future != null) future.complete(event);
            return;
        }
        event.fireEvent();
    }

    private static void log(String direction, SynergyEvent event) {
        if (trace) Synergy.getLogger().info("[trace] " + direction + " " + event.getIdentifier() + " #" + event.getId()
            + " from=" + event.getFrom() + " to=" + (event.isToPlayer() ? "@" + event.getPlayerUniqueId() : event.getTarget()) + " " + event.toJson().get("o"));
    }

    private static byte[] encodeAck(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("ack", id);
        return encode(json);
    }

    private static byte[] encode(JsonObject json) {
        String body = json.toString();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(body);
        out.writeUTF(sign(body));
        return out.toByteArray();
    }

    private static JsonObject decode(byte[] data) {
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String body = in.readUTF();
            if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8), in.readUTF().getBytes(StandardCharsets.UTF_8))) {
                if (!warned) Synergy.getLogger().warning("Plugin message with a wrong signature: messaging.token differs between the proxy and a backend");
                warned = true;
                if (!Synergy.isProxy()) return null;
            }
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Synergy.getSynergyToken().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 13);
    }
}
