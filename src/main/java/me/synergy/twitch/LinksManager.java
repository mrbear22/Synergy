package me.synergy.twitch;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.helix.domain.User;

import me.synergy.brains.Synergy;

public class LinksManager {

    public void linkAccount(UUID uuid, String username, String token) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return validateCredentials(username, token);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).thenAccept(isValid -> {
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
            CompletableFuture.runAsync(() -> {
                try {
                	Twitch.getConnectionManager().disconnect(channelName);
                } catch (Exception e) {
                }
            });
        }
        
        Synergy.getBread(uuid).setData("twitch-username", null);
        Synergy.getBread(uuid).setData("twitch-token", null);
        Synergy.getBread(uuid).sendMessage("<lang>twitch-unlinked</lang>");
    }
    
    private void processLinkResult(UUID uuid, String username, String token, boolean isValid) throws Exception {
        if (!isValid) {
            throw new RuntimeException("<lang>twitch-invalid-credentials</lang>");
        }
        
        String oldChannel = Synergy.getBread(uuid).getDataOrDefault("twitch-username", null).getAsString();
        if (oldChannel != null) {
        	Twitch.getConnectionManager().disconnect(oldChannel);
        }
        
        if (Twitch.getConnectionManager().connect(username.toLowerCase(), token)) {
            Synergy.getBread(uuid).setData("twitch-username", username.toLowerCase());
            Synergy.getBread(uuid).setData("twitch-token", token);
            Synergy.getBread(uuid).sendMessage("<lang>twitch-successfully-linked</lang>");
            
            String playerName = Synergy.getBread(uuid).getName();
            ChatHandler.sendMessage(username.toLowerCase(), "<lang>log-twitch-linked-message<arg>" + playerName + "</arg></lang>");
        } else {
            throw new RuntimeException("<lang>twitch-connection-failed</lang>");
        }
    }
    
    

    private boolean validateCredentials(String username, String token) throws Exception {
        if (username == null || username.isEmpty() || token == null || token.isEmpty()) {
            throw new RuntimeException("<lang>twitch-invalid-credentials</lang>");
        }
        
        // Basic token format check
        if (token.trim().length() < 10 || token.contains(" ")) {
            throw new RuntimeException("<lang>twitch-invalid-credentials</lang>");
        }
        
    	UUID uuid = Synergy.getDataManager().findUserUUID("twitch-username", username);
    	if (uuid != null) {
    		 throw new RuntimeException("<lang>link-twitch-already-linked<arg>"+Synergy.getBread(uuid).getName()+"</arg></lang>");
    	}
        
        TwitchClient testClient = null;
        try {
            OAuth2Credential credential = new OAuth2Credential("twitch", token);
            testClient = TwitchClientBuilder.builder()
                    .withClientId(Synergy.getConfig().getString("twitch.client-id"))
                    .withEnableHelix(true)
                    .withDefaultAuthToken(credential)
                    .build();

            List<User> users = testClient.getHelix().getUsers(token, null, List.of(username)).execute().getUsers();
            
            if (users.isEmpty() || !users.get(0).getLogin().equalsIgnoreCase(username)) {
                throw new RuntimeException("<lang>twitch-invalid-credentials</lang>");
            }
            
            return true;
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("<lang>twitch-invalid-credentials</lang>");
        } finally {
            if (testClient != null) {
                try {
                    testClient.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
    
    private String getErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "<lang>twitch-invalid-credentials</lang>";
        }
        
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        
        if (rootCause instanceof StringIndexOutOfBoundsException) {
            return "<lang>twitch-invalid-credentials</lang>";
        }
        
        String message = throwable.getMessage();
        if (message != null && message.contains("<lang>")) {
            return message;
        }
        
        return "<lang>twitch-invalid-credentials</lang>";
    }
}
