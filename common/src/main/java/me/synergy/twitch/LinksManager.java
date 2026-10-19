package me.synergy.twitch;

import me.synergy.modules.DataManager;import java.util.List;
import java.util.UUID;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.helix.domain.User;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Tasks;

public class LinksManager {

    public void linkAccount(UUID uuid, String username, String token) {
        Tasks.supply(() -> validateCredentials(username, token)).thenAccept(isValid -> {
            try {
                processLinkResult(uuid, username, token, isValid);
            } catch (Exception e) {
                Synergy.getBread(uuid).sendMessage(e.getMessage());
            }
        }).exceptionally(throwable -> {
            String errorMessage = getErrorMessage(throwable);
            Synergy.getBread(uuid).sendMessage(errorMessage);
            return null;
        });
    }
    
    public void unlinkAccount(UUID uuid) {
        String channelName = Synergy.getBread(uuid).getDataOrDefault("twitch-username", null).getAsString();
        
        if (channelName != null) {
            Tasks.async(() -> {
                try {
                    Twitch.getConnectionManager().disconnect(channelName);
                } catch (Exception e) {
                    Synergy.getLogger().warning("Twitch disconnect of " + channelName + " failed: " + e.getMessage());
                }
            });
        }
        
        Synergy.getBread(uuid).setData("twitch-username", null);
        Synergy.getBread(uuid).setData("twitch-token", null);
        Synergy.getBread(uuid).sendMessage("<locale:account.unlink.done service=Twitch>");
    }
    
    private void processLinkResult(UUID uuid, String username, String token, boolean isValid) throws Exception {
        if (!isValid) {
            throw new RuntimeException("<locale:twitch.error.credentials>");
        }
        
        String oldChannel = Synergy.getBread(uuid).getDataOrDefault("twitch-username", null).getAsString();
        if (oldChannel != null) {
        	Twitch.getConnectionManager().disconnect(oldChannel);
        }
        
        if (Twitch.getConnectionManager().connect(username.toLowerCase(), token)) {
            Synergy.getBread(uuid).setData("twitch-username", username.toLowerCase());
            Synergy.getBread(uuid).setData("twitch-token", token);
            Synergy.getBread(uuid).sendMessage("<locale:account.link.done service=Twitch>");
            
            String playerName = Synergy.getBread(uuid).getName();
            ChatHandler.sendMessage(username.toLowerCase(), "<locale:account.link.done service=Twitch>");
        } else {
            throw new RuntimeException("<locale:twitch.error.connection>");
        }
    }

    private boolean validateCredentials(String username, String token) throws Exception {
        if (username == null || username.isEmpty() || token == null || token.isEmpty()) {
            throw new RuntimeException("<locale:twitch.error.credentials>");
        }
        
        if (token.trim().length() < 10 || token.contains(" ")) {
            throw new RuntimeException("<locale:twitch.error.credentials>");
        }
        
    	UUID uuid = DataManager.findUserUUID("twitch-username", username);
    	if (uuid != null) {
    		 throw new RuntimeException("<locale:account.link.exists service=Twitch command=\"/twitch unlink\">");
    	}
        
        TwitchClient testClient = null;
        try {
            OAuth2Credential credential = new OAuth2Credential("twitch", token);
            testClient = TwitchClientBuilder.builder()
                    .withClientId(Config.getString("twitch.client-id"))
                    .withEnableHelix(true)
                    .withDefaultAuthToken(credential)
                    .build();

            List<User> users = testClient.getHelix().getUsers(token, null, List.of(username)).execute().getUsers();
            
            if (users.isEmpty() || !users.get(0).getLogin().equalsIgnoreCase(username)) {
                throw new RuntimeException("<locale:twitch.error.credentials>");
            }
            
            return true;
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("<locale:twitch.error.credentials>");
        } finally {
            if (testClient != null) {
                try {
                    testClient.close();
                } catch (Exception e) {
                }
            }
        }
    }
    
    private String getErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "<locale:twitch.error.credentials>";
        }
        
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        
        if (rootCause instanceof StringIndexOutOfBoundsException) {
            return "<locale:twitch.error.credentials>";
        }
        
        String message = throwable.getMessage();
        if (message != null && message.contains("<locale:")) {
            return message;
        }
        
        return "<locale:twitch.error.credentials>";
    }
}
