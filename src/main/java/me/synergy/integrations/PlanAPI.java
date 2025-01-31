package me.synergy.integrations;

import java.util.UUID;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.annotation.StringProvider;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;

import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;

@PluginInfo(name = "Synergy", iconName = "circle-nodes", iconFamily = Family.SOLID, color = Color.CYAN)
public class PlanAPI implements DataExtension {
	
	public void initialize() {
        try {
            ExtensionService.getInstance().register(this);
            Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
        } catch (IllegalStateException planIsNotEnabled) {
            Synergy.getLogger().error("Plan API is not active.");
        } catch (IllegalArgumentException dataExtensionImplementationIsInvalid) {
            Synergy.getLogger().error(dataExtensionImplementationIsInvalid.getMessage());
        }
	}
	
	@Override
	public CallEvents[] callExtensionMethodsOn() {
	    return new CallEvents[]{CallEvents.PLAYER_JOIN, CallEvents.PLAYER_LEAVE, CallEvents.SERVER_PERIODICAL};
	}
	
	@StringProvider(
	        text = "Pronoun",
	        description = "Player's pronoun.",
	        iconName = "venus-mars",
	        iconColor  = Color.AMBER
	)
	public String Pronoun(UUID playerUUID) {
	    return Synergy.getBread(playerUUID).getPronoun().name();
	}
	
	@StringProvider(
	        text = "Language",
	    	description = "Player's language.",
	        iconName = "earth-europe",
	        iconColor = Color.BLUE
	)
	public String Language(UUID playerUUID) {
	    return Synergy.getBread(playerUUID).getLanguage();
	}
	
	@StringProvider(
	        text = "Discord",
	    	description = "Player's discord tag.",
	        iconName = "user",
	        iconColor = Color.PURPLE
	)
	public String Discord(UUID playerUUID) {
		String userId = Discord.getDiscordIdByUniqueId(playerUUID);
		if (userId != null && Synergy.getDiscord() != null) {
		    return Synergy.getDiscord().getUserById(userId).getName();
		}
		return null;
	}
}