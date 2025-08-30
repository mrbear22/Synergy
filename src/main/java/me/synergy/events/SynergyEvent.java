package me.synergy.events;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.DataObject;
import net.md_5.bungee.api.config.ServerInfo;

public class SynergyEvent {

    private String identifier;
    private Map<String, String> options = new HashMap<>();
    private UUID uuid;
	
    public SynergyEvent(String identifier, UUID uuid, String options) {
        this.identifier = identifier;
        this.uuid = uuid;
        this.options = getOptionsAsMap(options);
	}

	public SynergyEvent(String identifier) {
        this.identifier = identifier;
	}

    public String getIdentifier() {
        return this.identifier;
    }

    public DataObject getOption(String option) {
        return new DataObject(options.get(option));
    }

    public Set<Entry<String, DataObject>> getOptions() {
    	Map<String, DataObject> newoptions = new HashMap<>();
    	options.entrySet().forEach(option->newoptions.put(option.getKey(), new DataObject(option.getValue())));
        return newoptions.entrySet();
    }
    
	public UUID getPlayerUniqueId() {
		return uuid;
	}

	public BreadMaker getBread() {
		return new BreadMaker(getPlayerUniqueId());
	}
	
	public OfflinePlayer getOfflinePlayer() {
		return getPlayerUniqueId() == null ? null : Bukkit.getOfflinePlayer(getPlayerUniqueId());
	}
	
	private String getOptionsAsJson() {
        Gson gson = new Gson();
        return gson.toJson(options);
    }

    private Map<String, String> getOptionsAsMap(String options) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        return gson.fromJson(options, type);
    }
	
    public SynergyEvent setOption(String option, String value) {
        this.options.put(option, value);
        return this;
    }

	public SynergyEvent setPlayerUniqueId(UUID UUID) {
		this.uuid = UUID;
        return this;
	}
	
    public void send() {
        if (Synergy.getConfig().getBoolean("synergy-plugin-messaging.enabled")) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            if (Synergy.isRunningBungee() || Synergy.isRunningVelocity()) {
                out.writeUTF(Synergy.getSynergyToken());
            }
            out.writeUTF(getIdentifier());
            out.writeUTF(String.valueOf(getPlayerUniqueId()));
            out.writeUTF(getOptionsAsJson());
            if (Synergy.isRunningSpigot()) {
            	Synergy.getSpigot().sendPluginMessage(out.toByteArray());
            }
            if (Synergy.isRunningBungee()) {
	            for (Entry<String, ServerInfo> server : Bungee.getInstance().getProxy().getServers().entrySet()) {
	            	server.getValue().sendData("net:synergy", out.toByteArray());
	            } 
            }
        } else {
        	fireEvent();
        }
    }
    
	public void fireEvent() {
    	Synergy.getEventManager().fireEvent(new SynergyEvent(getIdentifier(), getPlayerUniqueId(), getOptionsAsJson()));
	}

}
