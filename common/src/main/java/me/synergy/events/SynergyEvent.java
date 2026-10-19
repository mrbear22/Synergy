package me.synergy.events;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import me.synergy.brains.Synergy;
import me.synergy.modules.Messaging;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.DataObject;

public class SynergyEvent {

    private static final Gson GSON = new Gson();

    private final String identifier;
    private final Map<String, JsonElement> options = new HashMap<>();
    private UUID uuid;
    private String id = Messaging.newId(), from = Synergy.getServerName(), target, replyTo;
    private boolean toPlayer;

    public SynergyEvent(String identifier) {
        this.identifier = identifier;
    }

    public SynergyEvent(String identifier, UUID uuid, String options) {
        this(identifier);
        this.uuid = uuid;
        if (options != null) GSON.fromJson(options, JsonObject.class).entrySet().forEach(e -> this.options.put(e.getKey(), e.getValue()));
    }

    public String getIdentifier() { return identifier; }
    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTarget() { return target; }
    public String getReplyTo() { return replyTo; }
    public boolean isToPlayer() { return toPlayer; }
    public UUID getPlayerUniqueId() { return uuid; }
    public BreadMaker getBread() { return new BreadMaker(uuid); }

    public DataObject getOption(String option) {
        return wrap(options.get(option));
    }

    public Set<Entry<String, DataObject>> getOptions() {
        Map<String, DataObject> result = new HashMap<>();
        options.forEach((k, v) -> result.put(k, wrap(v)));
        return result.entrySet();
    }

    public String getOptionsAsJson() {
        return GSON.toJson(options);
    }

    public SynergyEvent setOption(String option, Object value) {
        options.put(option, value == null ? null : value instanceof String s ? new JsonPrimitive(s) : GSON.toJsonTree(value));
        return this;
    }

    public SynergyEvent setPlayerUniqueId(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public SynergyEvent to(String server) {
        this.target = server;
        return this;
    }

    public SynergyEvent toProxy() {
        return to(Messaging.PROXY);
    }

    public SynergyEvent toPlayer(UUID uuid) {
        this.uuid = uuid;
        this.toPlayer = true;
        return this;
    }

    public SynergyEvent reply() {
        SynergyEvent reply = new SynergyEvent(identifier).to(from).setPlayerUniqueId(uuid);
        reply.replyTo = id;
        return reply;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void send() {
        Messaging.send(this);
    }

    public CompletableFuture<SynergyEvent> request() {
        return request(Duration.ofSeconds(10));
    }

    public CompletableFuture<SynergyEvent> request(Duration timeout) {
        return Messaging.request(this, timeout);
    }

    public void fireEvent() {
        Synergy.getEventManager().fireEvent(this);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("e", identifier);
        if (uuid != null) json.addProperty("p", uuid.toString());
        json.add("o", GSON.toJsonTree(options));
        json.addProperty("from", from);
        if (target != null) json.addProperty("to", target);
        if (toPlayer) json.addProperty("tp", true);
        if (replyTo != null) json.addProperty("re", replyTo);
        return json;
    }

    public static SynergyEvent fromJson(JsonObject json) {
        SynergyEvent event = new SynergyEvent(json.get("e").getAsString());
        event.id = json.get("id").getAsString();
        if (json.has("p")) event.uuid = UUID.fromString(json.get("p").getAsString());
        json.getAsJsonObject("o").entrySet().forEach(e -> event.options.put(e.getKey(), e.getValue()));
        event.from = json.has("from") ? json.get("from").getAsString() : null;
        event.target = json.has("to") ? json.get("to").getAsString() : null;
        event.toPlayer = json.has("tp");
        event.replyTo = json.has("re") ? json.get("re").getAsString() : null;
        return event;
    }

    private static DataObject wrap(JsonElement value) {
        return new DataObject(value == null || value.isJsonNull() ? null : value.isJsonPrimitive() ? value.getAsString() : value);
    }
}
