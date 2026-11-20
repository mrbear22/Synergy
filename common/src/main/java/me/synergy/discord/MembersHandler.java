package me.synergy.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.PlayerSessions;
import me.synergy.modules.Config;
import me.synergy.text.Translation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ItemComponent;

public class MembersHandler extends ListenerAdapter implements SynergyListener {
	
    public MembersHandler() {
        Synergy.getEventManager().registerEvents(this);
    }
	
	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
		String buttonId = event.getComponentId();
		
		if (buttonId.startsWith("msg:")) {
			String message = buttonId.substring(4);
			String translatedMessage = Synergy.translate(message, Translation.getDefaultLanguage()).getStripped();
			event.reply(translatedMessage).setEphemeral(true).queue();
		}
	}
	
	@Override
	public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
		if (!Config.getBoolean("discord.welcome-message.enabled", false)) {
			return;
		}
		
		User user = event.getUser();
		String welcomeMessage = Synergy.translate(Config.getString("discord.welcome-message.text"), Translation.getDefaultLanguage()).getStripped().replace("%NAME%", user.getAsMention());
		
		user.openPrivateChannel().queue(channel -> {
			List<ItemComponent> buttons = new ArrayList<>();
			
			Map<String, Object> buttonConfigs = Config.getConfigurationSection("discord.welcome-message.buttons");
			
			if (buttonConfigs != null && !buttonConfigs.isEmpty()) {

				for (Map.Entry<String, Object> entry : buttonConfigs.entrySet()) {
					String key = entry.getKey();
					
					String label = Config.getString("discord.welcome-message.buttons." + key + ".label");
					String value = Config.getString("discord.welcome-message.buttons." + key + ".value");
					String style = Config.getString("discord.welcome-message.buttons." + key + ".style", "primary");
					String emoji = Config.getString("discord.welcome-message.buttons." + key + ".emoji");
					
					if (label == null || value == null) {
						continue;
					}
					
					Button button;
					
					if (value.startsWith("http://") || value.startsWith("https://")) {
						button = Button.link(value, Synergy.translate(label, Translation.getDefaultLanguage()).getStripped());
					} else {
						String buttonId = "msg:" + value;
						
						switch (style.toLowerCase()) {
							case "success":
							case "green":
								button = Button.success(buttonId, Synergy.translate(label, Translation.getDefaultLanguage()).getStripped());
								break;
							case "danger":
							case "red":
								button = Button.danger(buttonId, Synergy.translate(label, Translation.getDefaultLanguage()).getStripped());
								break;
							case "secondary":
							case "gray":
							case "grey":
								button = Button.secondary(buttonId, Synergy.translate(label, Translation.getDefaultLanguage()).getStripped());
								break;
							case "primary":
							case "blue":
							default:
								button = Button.primary(buttonId, Synergy.translate(label, Translation.getDefaultLanguage()).getStripped());
								break;
						}
					}
					
					if (emoji != null && !emoji.isEmpty()) {
						try {
							button = button.withEmoji(Emoji.fromFormatted(emoji));
						} catch (Exception e) {
							Synergy.getLogger().warning("Invalid emoji format for button " + key + ": " + emoji);
						}
					}
					
					buttons.add(button);
					
					if (buttons.size() >= 5) {
						break;
					}
				}
			}
			
			if (buttons.isEmpty()) {
				channel.sendMessage(welcomeMessage).queue(
					success -> Synergy.getLogger().info("Welcome message sent to " + user.getName()),
					error -> Synergy.getLogger().warning("Failed to send welcome message to " + user.getName() + ": " + error.getMessage())
				);
			} else {
				channel.sendMessage(welcomeMessage)
					.setActionRow(buttons)
					.queue(
						success -> Synergy.getLogger().info("Welcome message sent to " + user.getName()),
						error -> Synergy.getLogger().warning("Failed to send welcome message to " + user.getName() + ": " + error.getMessage())
					);
			}
		}, error -> {
			Synergy.getLogger().warning("Cannot open private channel with " + user.getName() + ": " + error.getMessage());
		});
	}

    @SynergyHandler("discord-reasons")
    public void onReasons(SynergyEvent event) {
        event.reply().setOption("reasons", String.join(",", Discord.reasons(event.getBread()))).send();
    }

    /** Any membership change on our guild re-checks the player's real Discord state, the same check as on join. */
    private static void recheck(Guild guild, User user) {
        Guild ours = Discord.getGuild();
        if (ours == null || !ours.getId().equals(guild.getId())) return;
        PlayerSessions.discord(Discord.getUniqueIdByDiscordId(user.getId()));
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        recheck(event.getGuild(), event.getUser());
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        recheck(event.getGuild(), event.getUser());
    }

    @Override
    public void onGuildMemberUpdateTimeOut(@NotNull GuildMemberUpdateTimeOutEvent event) {
        recheck(event.getGuild(), event.getUser());
    }
}
