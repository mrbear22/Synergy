package me.synergy.modules;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.Cache;
import me.synergy.objects.DataObject;

public class DataManager implements Module {

    private interface Query<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final long CACHE_SECONDS = 600;
    private static final Map<String, UUID> owners = new ConcurrentHashMap<>();
    private static Connection connection;

    @Override
    public void defaults() {
        Config.addDefault("storage.type", "sqlite");
        Config.addDefault("storage.host", new java.io.File(Synergy.getDataFolder(), "data.db").getPath());
        Config.addDefault("storage.user", "user");
        Config.addDefault("storage.port", "3306");
        Config.addDefault("storage.password", "password");
        Config.addDefault("storage.database", "database");
    }

    @Override
    public void start() throws SQLException {
        for (String driver : new String[] {"org.sqlite.JDBC", "com.mysql.cj.jdbc.Driver"}) {
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException ignored) {}
        }
        if (Messaging.isEnabled() && sqlite())
            Synergy.getLogger().warning("storage.type is sqlite in a network: every server keeps its own data.db, so links, languages and themes are not shared. Use mysql");
        connect();
        Synergy.getEventManager().registerEvents(DataManager.class);
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void refresh() throws SQLException {
        connect();
    }

    public static synchronized void connect() throws SQLException {
        close();
        connection = sqlite()
            ? DriverManager.getConnection("jdbc:sqlite:" + Config.getString("storage.host"))
            : DriverManager.getConnection("jdbc:mysql://" + Config.getString("storage.host") + ":" + Config.getString("storage.port") + "/" + Config.getString("storage.database") + "?useSSL=false&serverTimezone=UTC",
                Config.getString("storage.user"), Config.getString("storage.password"));
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS synergy (uuid VARCHAR(36) NOT NULL, option VARCHAR(255) NOT NULL, value TEXT, expires BIGINT, PRIMARY KEY (uuid, option))");
        }
        tolerate(sqlite() ? "CREATE INDEX IF NOT EXISTS synergy_lookup ON synergy (option, value)" : "CREATE INDEX synergy_lookup ON synergy (option, value(191))");
    }

    private static boolean sqlite() {
        return !"mysql".equalsIgnoreCase(Config.getString("storage.type"));
    }

    private static void tolerate(String sql) {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ignored) {}
    }

    private static synchronized void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {}
        connection = null;
    }

    private static synchronized <T> T sql(T fallback, Query<T> query) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (connection == null || connection.isClosed()) connect();
                return query.run(connection);
            } catch (SQLException e) {
                if (attempt == 1) Synergy.getLogger().error("Database error: " + e.getMessage());
                close();
            }
        }
        return fallback;
    }

    public static DataObject getData(UUID uuid, String option) {
        return getDataOrDefault(uuid, option, null);
    }

    public static DataObject getDataOrDefault(UUID uuid, String option, Object fallback) {
        if (uuid == null) return new DataObject(fallback);
        Cache cache = new Cache(uuid);
        DataObject cached = cache.get(option);
        if (cached != null) return cached.isSet() ? cached : new DataObject(fallback);
        String value = sql(null, c -> {
            try (PreparedStatement statement = c.prepareStatement("SELECT value FROM synergy WHERE uuid = ? AND option = ? AND (expires IS NULL OR expires > ?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, option);
                statement.setLong(3, System.currentTimeMillis());
                ResultSet result = statement.executeQuery();
                return result.next() ? result.getString(1) : null;
            }
        });
        cache.add(option, value, CACHE_SECONDS);
        return new DataObject(value != null ? value : fallback);
    }

    public static void setData(UUID uuid, String option, Object value) {
        setData(uuid, option, value, null);
    }

    public static void setData(UUID uuid, String option, Object value, Duration ttl) {
        if (uuid == null) return;
        Long expires = ttl == null ? null : System.currentTimeMillis() + ttl.toMillis();
        sql(null, c -> {
            try (PreparedStatement statement = c.prepareStatement(value == null ? "DELETE FROM synergy WHERE uuid = ? AND option = ?" : "REPLACE INTO synergy (uuid, option, value, expires) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, option);
                if (value != null) {
                    statement.setString(3, value.toString());
                    statement.setObject(4, expires);
                }
                return statement.executeUpdate();
            }
        });
        remember(uuid, option, value == null ? null : value.toString(), ttl);
        SynergyEvent event = Synergy.event("update-bread-cache").setPlayerUniqueId(uuid).setOption("option", option);
        if (value != null) event.setOption("value", value.toString());
        if (ttl != null) event.setOption("ttl", ttl.getSeconds());
        event.send();
    }

    @SynergyHandler("update-bread-cache")
    public static void onSynergyEvent(SynergyEvent event) {
        Long ttl = event.getOption("ttl").getAsLong();
        remember(event.getPlayerUniqueId(), event.getOption("option").getAsString(), event.getOption("value").getAsString(), ttl == null ? null : Duration.ofSeconds(ttl));
    }

    private static void remember(UUID uuid, String option, String value, Duration ttl) {
        new Cache(uuid).add(option, value, ttl == null ? CACHE_SECONDS : Math.min(CACHE_SECONDS, ttl.getSeconds()));
        owners.entrySet().removeIf(e -> e.getKey().startsWith(option + ":") && (e.getValue().equals(uuid) || e.getKey().equals(option + ":" + value)));
    }

    public static UUID findUserUUID(String option, String value) {
        if (value == null) return null;
        String key = option + ":" + value;
        UUID cached = owners.get(key);
        if (cached != null) return cached;
        UUID found = sql(null, c -> {
            try (PreparedStatement statement = c.prepareStatement("SELECT uuid FROM synergy WHERE option = ? AND value = ? AND (expires IS NULL OR expires > ?)")) {
                statement.setString(1, option);
                statement.setString(2, value);
                statement.setLong(3, System.currentTimeMillis());
                ResultSet result = statement.executeQuery();
                return result.next() ? UUID.fromString(result.getString(1)) : null;
            }
        });
        if (found != null) owners.put(key, found);
        return found;
    }

    public static UUID findUuidByName(String name) {
        return findUserUUID("name", name);
    }

    public static void migrateUuid(UUID oldUuid, UUID newUuid) {
        if (oldUuid.equals(newUuid)) return;
        sql(null, c -> {
            c.setAutoCommit(false);
            try (PreparedStatement copy = c.prepareStatement("REPLACE INTO synergy (uuid, option, value, expires) SELECT ?, option, value, expires FROM synergy WHERE uuid = ?");
                 PreparedStatement delete = c.prepareStatement("DELETE FROM synergy WHERE uuid = ?")) {
                copy.setString(1, newUuid.toString());
                copy.setString(2, oldUuid.toString());
                copy.executeUpdate();
                delete.setString(1, oldUuid.toString());
                delete.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
            return null;
        });
        new Cache(oldUuid).clear();
        new Cache(newUuid).clear();
        owners.values().removeIf(oldUuid::equals);
    }
}
