package me.synergy.twitch;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.eventsub.events.CustomRewardRedemptionAddEvent;
import com.github.twitch4j.helix.domain.CustomReward;
import com.github.twitch4j.helix.domain.User;

import me.synergy.brains.Synergy;

public class RewardManager {

    private String channelName;
    private String accessToken;

    public RewardManager(String channelName, String accessToken) {
        this.channelName = channelName;
        this.accessToken = accessToken;
    }

    public void create(String title, Integer cost, String description, boolean inputRequired) throws Exception {
        if (title == null || title.isEmpty()) {
            throw new RuntimeException("<lang>reward-title-empty</lang>");
        }

        // Отримуємо User ID користувача
        String userId = getUserId(channelName, accessToken);
        if (userId == null) {
            throw new RuntimeException("<lang>reward-user-id-failed</lang>");
        }

        TwitchClient client = ConnectionManager.channelClients.get(channelName);
        if (client == null) {
            throw new RuntimeException("<lang>reward-client-not-found</lang>");
        }

        try {

            CustomReward customReward = CustomReward.builder()
                    .title(title)
                    .prompt(description)
                    .cost(cost)
                    .isEnabled(true)
                    .isUserInputRequired(inputRequired)
                    .build();

            client.getHelix().createCustomReward(accessToken, userId, customReward).execute();
            
            UUID uuid = Synergy.getDataManager().findUserUUID("twitch-username", channelName);
            Synergy.getBread(uuid).sendMessage("<lang>reward-create-success</lang>");
            
            Synergy.getLogger().info("[Twitch] ["+channelName+"] Successfully created reward: " + title);

        } catch (Exception e) {
            String message = e.getMessage();
            Synergy.getLogger().error("[Twitch] ["+channelName+"] Error creating reward: " + message);
            e.printStackTrace();
            throw new RuntimeException("<lang>reward-create-failed</lang>");
        }
    }

    public void delete(String title) throws Exception {
        if (title == null || title.isEmpty()) {
            throw new RuntimeException("<lang>reward-title-empty</lang>");
        }

        String userId = getUserId(channelName, accessToken);
        if (userId == null) {
            throw new RuntimeException("<lang>reward-user-id-failed</lang>");
        }

        TwitchClient client = ConnectionManager.channelClients.get(channelName);
        if (client == null) {
            throw new RuntimeException("<lang>reward-client-not-found</lang>");
        }

        try {
            List<CustomReward> rewards = client.getHelix()
                    .getCustomRewards(accessToken, userId, null, false)
                    .execute()
                    .getRewards();

            if (rewards == null || rewards.isEmpty()) {
                throw new RuntimeException("<lang>reward-no-rewards-found</lang>");
            }

            List<CustomReward> matchingRewards = rewards.stream()
                    .filter(reward -> reward.getTitle().equalsIgnoreCase(title))
                    .collect(Collectors.toList());

            if (matchingRewards.isEmpty()) {
                throw new RuntimeException("<lang>reward-not-found</lang>");
            }

            for (CustomReward reward : matchingRewards) {
                client.getHelix().deleteCustomReward(accessToken, userId, reward.getId()).execute();
            }
            

            UUID uuid = Synergy.getDataManager().findUserUUID("twitch-username", channelName);
            Synergy.getBread(uuid).sendMessage("<lang>reward-delete-success</lang>");

        } catch (Exception e) {
            String message = e.getMessage();
            Synergy.getLogger().error("[Twitch] ["+channelName+"] Error deleting reward: " + message);
            
            e.printStackTrace();

            throw new RuntimeException("<lang>reward-delete-failed</lang>");
        }
    }
    
    public static void handleRewardRedeemedEventSub(CustomRewardRedemptionAddEvent event, String channelName) {
        String rewardTitle = event.getReward().getTitle();
        String viewerName = event.getUserName();
        String userInput = event.getUserInput();
        
        UUID playerUuid = getPlayerUuidByChannel(channelName);
        if (playerUuid == null) return;

        Synergy.createSynergyEvent("twitch-reward-redeemed")
                .setPlayerUniqueId(playerUuid)
                .setOption("reward-title", rewardTitle)
                .setOption("viewer-name", viewerName)
                .setOption("channel-name", channelName)
                .setOption("reward-id", event.getReward().getId())
                .setOption("viewer-input", userInput != null ? userInput : null)
                .send();
                
        Synergy.getLogger().info("[Twitch] ["+channelName+"] " + viewerName + " redeemed: " + rewardTitle);
    }
    
    private static UUID getPlayerUuidByChannel(String channel) {
        try {
            return Synergy.getDataManager().findUserUUID("twitch-username", channel);
        } catch (SQLException e) {
            return null;
        }
    }
    
    private static String getUserId(String username, String accessToken) {
        try {
            TwitchClient client = ConnectionManager.channelClients.get(username);
            if (client == null) {
                Synergy.getLogger().error("[Twitch] Client not found for channel: " + username);
                return null;
            }

            String normalizedUsername = username.toLowerCase();
            
            Synergy.getLogger().info("[Twitch] Attempting to get user ID for: " + normalizedUsername);
            
            if (accessToken == null || accessToken.trim().isEmpty()) {
                Synergy.getLogger().error("[Twitch] Access token is null or empty for user: " + username);
                return null;
            }
            
            List<User> users = client.getHelix()
                    .getUsers(accessToken, null, List.of(normalizedUsername))
                    .execute()
                    .getUsers();
            
            if (users == null || users.isEmpty()) {
                Synergy.getLogger().error("[Twitch] No user found for username: " + normalizedUsername);
                return null;
            }
            
            String userId = users.get(0).getId();
            Synergy.getLogger().info("[Twitch] Successfully got user ID: " + userId + " for username: " + normalizedUsername);
            
            return userId;
            
        } catch (Exception e) {
            Synergy.getLogger().error("[Twitch] Error getting user ID for " + username + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}