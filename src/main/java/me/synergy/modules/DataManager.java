package me.synergy.modules;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.Cache;
import me.synergy.objects.DataObject;
import me.synergy.utils.Timings;

public class DataManager implements SynergyListener {

    private static Connection connection;
    
    public void initialize() {
        try {
        	establishConnection();
        	Synergy.getEventManager().registerEvents(this);
	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
        } catch (Exception c) {
	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
        }
    }

    private void establishConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            Config config = Synergy.getConfig();
            String dbType = config.getString("storage.type");
            String host = config.getString("storage.host");
            String database = config.getString("storage.database");
            String user = config.getString("storage.user");
            String port = config.getString("storage.port");
            String password = config.getString("storage.password");

            if (dbType.equalsIgnoreCase("sqlite")) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + host);
            } else if (dbType.equalsIgnoreCase("mysql")) {
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                             "?useSSL=false&serverTimezone=UTC";
                connection = DriverManager.getConnection(url, user, password);
            }

            createTable();
        }
    }
    
    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS synergy (" +
                "uuid VARCHAR(36) NOT NULL," +
                "option VARCHAR(255) NOT NULL," +
                "value TEXT," +
                "PRIMARY KEY (uuid, option)" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    public DataObject getData(UUID uuid, String option) throws SQLException {
    	return getData(uuid, option.toString(), null, true);
    }
    
    public DataObject getData(UUID uuid, String option, boolean useCache) throws SQLException {
    	return getData(uuid, option.toString(), null, useCache);
    }
    
    public DataObject getDataOrDefault(UUID uuid, String option, Object defaultValue) throws SQLException {
    	return getData(uuid, option.toString(), defaultValue, true);
    }
    
    public DataObject getDataOrDefault(UUID uuid, String option, Object defaultValue, boolean useCache) throws SQLException {
    	return getData(uuid, option.toString(), defaultValue, useCache);
    }
    
    public DataObject getData(UUID uuid, String option, Object defaultValue, boolean useCache) throws SQLException {

        Timings timing = new Timings();
        timing.startTiming("Data-Get");

        Cache cache = new Cache(uuid);
        String key = option;

        if (useCache && !cache.isExpired(key)) {
            timing.endTiming("Data-Get");
            return cache.get(key);
        }

        establishConnection();
        String sql = "SELECT value FROM synergy WHERE uuid = ? AND option = ?";
        String value = null;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                value = rs.getString("value");
            }
        }

        Object data = (value != null) ? value : defaultValue;

        cache.add(key, data, 600);

        timing.endTiming("Data-Get");

        return new DataObject(data);
    }

    
    public void setData(UUID uuid, String option, Object value) throws SQLException {
    	Timings timing = new Timings();
    	timing.startTiming("Data-Set");
    	establishConnection();
        String sql = value == null ? "DELETE FROM synergy WHERE uuid = ? AND option = ?"
        						   : "REPLACE INTO synergy (uuid, option, value) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, option);
            if (value != null) {
                pstmt.setString(3, value.toString());
            }
            pstmt.executeUpdate();
            new Cache(uuid).add(option, value, 600);
        }
        Synergy.createSynergyEvent("update-bread-cache").setPlayerUniqueId(uuid).setOption("option", option).setOption("value", value.toString()).send();
    	timing.endTiming("Data-Set");
    }
    
    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
    	if (!event.getIdentifier().equals("update-bread-cache")) {
    		return;
    	}
    	String option = event.getOption("option").getAsString();
    	String value = event.getOption("value").isSet() ? event.getOption("value").getAsString() : null;
    	new Cache(event.getPlayerUniqueId()).add(option, value, 600);
    }
    
    public UUID findUserUUID(String option, String value) throws SQLException {
    	establishConnection();
        String sql = "SELECT uuid FROM synergy WHERE option = ? AND value = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, option);
            pstmt.setString(2, value);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DataObject(rs.getString("uuid")).getAsUUID();
            } else {
                return null;
            }
        }
    }
    
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
