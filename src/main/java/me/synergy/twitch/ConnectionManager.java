package me.synergy.twitch;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionAddEvent;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.helix.domain.User;

import me.synergy.brains.Synergy;

public class ConnectionManager {

	static final ConcurrentHashMap<String, TwitchClient> channelClients = new ConcurrentHashMap<>();
	
    public boolean connect(String channelName, String accessToken) throws Exception {
        if (channelName == null || channelName.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("<lang>twitch-invalid-credentials</lang>");
        }
        
        if (channelClients.containsKey(channelName)) {
            return true;
        }
        
        if (Synergy.getConfig().getString("twitch.client-id").equals("client-id")) {
            return true;
        }
        
        try {
            OAuth2Credential credential = new OAuth2Credential("twitch", accessToken);
            
            TwitchClient client = TwitchClientBuilder.builder()
                    .withClientId(Synergy.getConfig().getString("twitch.client-id"))
                    .withEnableHelix(true)
                    .withEnableChat(true)
                    .withEnableEventSocket(true)
                    .withChatAccount(credential)
                    .withDefaultAuthToken(credential)
                    .build();

            client.getChat().joinChannel(channelName);
            client.getEventManager().onEvent(ChannelMessageEvent.class, e -> 
            	ChatHandler.handleChatMessage(e, channelName)
            );
            client.getEventManager().onEvent(CustomRewardRedemptionAddEvent.class, event -> 
            	RewardManager.handleRewardRedeemedEventSub(event, channelName)
            );

            String userIdForEventSub = null;
            try {
                List<User> users = client.getHelix().getUsers(accessToken, null, List.of(channelName)).execute().getUsers();
                if (!users.isEmpty()) {
                    userIdForEventSub = users.get(0).getId();
                }
            } catch (Exception e) {
                Synergy.getLogger().error("<lang>twitch-failed-get-user-id</lang>" + e.getMessage());
            }
            final String userId = userIdForEventSub;
            if (userId != null) {
                try {
                    client.getEventSocket().register(SubscriptionTypes.CHANNEL_POINTS_CUSTOM_REWARD_REDEMPTION_ADD.prepareSubscription(
                        builder -> builder.broadcasterUserId(userId).build(),
                        null
                    ));
                    
                    Synergy.getLogger().info("<lang>twitch-eventsub-subscription-created</lang>" + channelName + " (User ID: " + userId + ")");
                } catch (Exception e) {
                    Synergy.getLogger().error("<lang>twitch-failed-create-eventsub</lang>" + e.getMessage());
                }
            }

            channelClients.put(channelName, client);
            return true;
            
        } catch (Exception e) {
            throw new RuntimeException("<lang>twitch-connection-failed</lang>");
        }
    }
    
    public void disconnect(String channelName) throws Exception {
        if (channelName == null || channelName.isEmpty()) {
            return;
        }

        if (Synergy.getConfig().getString("twitch.client-id").equals("client-id")) {
            return;
        }
        
        TwitchClient client = channelClients.remove(channelName);
        if (client != null) {
            try {
                client.getChat().leaveChannel(channelName);
                if (client.getEventSocket() != null) {
                    client.getEventSocket().close();
                }
                client.close();
            } catch (Exception e) {
                throw new RuntimeException("<lang>twitch-connection-failed</lang>");
            }
        }
    }
}