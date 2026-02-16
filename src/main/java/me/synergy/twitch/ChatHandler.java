package me.synergy.twitch;
import java.util.UUID;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;

import me.synergy.brains.Synergy;
import me.synergy.text.Translation;

public class ChatHandler {

    public static void handleChatMessage(ChannelMessageEvent event, String channelName) {
        String message = event.getMessage();
        String username = event.getUser().getName();
        
        Synergy.event("twitch-chat")
               .setOption("player", username)
               .setOption("message", message)
               .setOption("twitch-channel", channelName)
               .send();
    }

    static void sendMessage(String channelName, String message) {
    	try {
			UUID uuid = Synergy.getDataManager().findUserUUID("twitch-username", channelName);
	        TwitchClient client = ConnectionManager.channelClients.get(channelName);
	        if (client != null && client.getChat() != null) {
	            client.getChat().sendMessage(channelName, Translation.translate(message, Synergy.getBread(uuid).getLanguage()));
	        }
		} catch (Exception e) {
		}
    }
}