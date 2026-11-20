package me.synergy.modules;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    public interface Row<T> {
        T map(ResultSet row) throws SQLException;
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

    public static boolean sqlite() {
        return !"mysql".equalsIgnoreCase(Config.getString("storage.type"));
    }

    /** Runs DDL that may already be applied (an index that exists, a column already added). */
    public static void tolerate(String sql) {
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

    /** INSERT/UPDATE/DELETE with positional parameters; returns the affected rows, or -1 when the database is unreachable. */
    public static int update(String query, Object... params) {
        return sql(-1, c -> {
            try (PreparedStatement statement = c.prepareStatement(query)) {
                bind(statement, params);
                return statement.executeUpdate();
            }
        });
    }

    /** INSERT returning the generated key, or -1 on failure. */
    public static long insert(String query, Object... params) {
        return sql(-1L, c -> {
            try (PreparedStatement statement = c.prepareStatement(query, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, params);
                statement.executeUpdate();
                ResultSet keys = statement.getGeneratedKeys();
                return keys.next() ? keys.getLong(1) : -1L;
            }
        });
    }

    public static <T> List<T> select(String query, Row<T> mapper, Object... params) {
        return sql(List.of(), c -> {
            try (PreparedStatement statement = c.prepareStatement(query)) {
                bind(statement, params);
                ResultSet result = statement.executeQuery();
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(mapper.map(result));
                return rows;
            }
        });
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
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

    /** Writes player data for outside tools (the website): unlike a direct UPDATE, every server's cache follows. Options: option, value (absent = delete), ttl seconds. */
    @SynergyHandler("data-set")
    public static void onDataSet(SynergyEvent event) {
        String option = event.getOption("option").getAsString();
        if (event.getPlayerUniqueId() == null || option == null || option.isBlank()) {
            event.reply().setOption("error", "uuid and option are required").send();
            return;
        }
        Long ttl = event.getOption("ttl").getAsLong();
        setData(event.getPlayerUniqueId(), option, event.getOption("value").getAsString(), ttl == null ? null : Duration.ofSeconds(ttl));
        event.reply().setOption("ok", true).send();
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
}
