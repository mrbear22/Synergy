package me.synergy.objects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** One row of the punishments table: the log entry and, while it is not revoked or expired, an active punishment. */
public record Punishment(long id, UUID uuid, Type type, String reason, String staff, String staffUuid, String source,
                         long created, Long expires, Long revoked, String revokedBy, String revokeReason) {

    public enum Type {
        WARN, MUTE, BAN;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Type of(String id) {
            try {
                return id == null ? null : valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public static final String COLUMNS = "id, uuid, type, reason, staff, staff_uuid, source, created, expires, revoked, revoked_by, revoke_reason";

    public boolean permanent() {
        return expires == null;
    }

    public boolean active() {
        return revoked == null && (expires == null || expires > System.currentTimeMillis());
    }

    /** Seconds left, -1 for a permanent punishment. */
    public long remainingSeconds() {
        return expires == null ? -1 : Math.max(0, (expires - System.currentTimeMillis()) / 1000);
    }

    public static Punishment of(ResultSet row) throws SQLException {
        return new Punishment(row.getLong("id"), UUID.fromString(row.getString("uuid")), Type.of(row.getString("type")), row.getString("reason"),
            row.getString("staff"), row.getString("staff_uuid"), row.getString("source"), row.getLong("created"),
            nullable(row, "expires"), nullable(row, "revoked"), row.getString("revoked_by"), row.getString("revoke_reason"));
    }

    private static Long nullable(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("uuid", uuid.toString());
        json.addProperty("type", type.id());
        json.addProperty("reason", reason);
        json.addProperty("staff", staff);
        json.addProperty("staff_uuid", staffUuid);
        json.addProperty("source", source);
        json.addProperty("created", created);
        json.addProperty("expires", expires);
        json.addProperty("revoked", revoked);
        json.addProperty("revoked_by", revokedBy);
        json.addProperty("revoke_reason", revokeReason);
        return json;
    }

    public static Punishment fromJson(JsonObject json) {
        return new Punishment(json.get("id").getAsLong(), UUID.fromString(json.get("uuid").getAsString()), Type.of(json.get("type").getAsString()),
            string(json, "reason"), string(json, "staff"), string(json, "staff_uuid"), string(json, "source"), json.get("created").getAsLong(),
            number(json, "expires"), number(json, "revoked"), string(json, "revoked_by"), string(json, "revoke_reason"));
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static Long number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsLong();
    }
}
