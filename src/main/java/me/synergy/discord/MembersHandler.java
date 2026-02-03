package me.synergy.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Translation;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ItemComponent;

public class MembersHandler extends ListenerAdapter implements SynergyListener {
	
	public MembersHandler() {
        try {
	        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
	            return;
	        }
	        
	        Synergy.getEventManager().registerEvents(this);
	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	    } catch (Exception exception) {
	        Synergy.getLogger().error(String.valueOf(getClass().getSimpleName()) + " module failed to initialize: " + exception.getMessage());
	    	exception.printStackTrace();
	    }
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
		if (!Synergy.getConfig().getBoolean("discord.welcome-message.enabled", false)) {
			return;
		}
		
		User user = event.getUser();
		String welcomeMessage = Synergy.translate(Synergy.getConfig().getString("discord.welcome-message.text", "Welcome, %NAME%!"), Translation.getDefaultLanguage()).getStripped().replace("%NAME%", user.getAsMention());
		
		user.openPrivateChannel().queue(channel -> {
			List<ItemComponent> buttons = new ArrayList<>();
			
			Map<String, Object> buttonConfigs = Synergy.getConfig().getConfigurationSection("discord.welcome-message.buttons");
			
			if (buttonConfigs != null && !buttonConfigs.isEmpty()) {

				for (Map.Entry<String, Object> entry : buttonConfigs.entrySet()) {
					String key = entry.getKey();
					
					String label = Synergy.getConfig().getString("discord.welcome-message.buttons." + key + ".label");
					String value = Synergy.getConfig().getString("discord.welcome-message.buttons." + key + ".value");
					String style = Synergy.getConfig().getString("discord.welcome-message.buttons." + key + ".style", "primary");
					String emoji = Synergy.getConfig().getString("discord.welcome-message.buttons." + key + ".emoji");
					
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

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
	        String userId = event.getUser().getId();
	        UUID uniqueId = Discord.getUniqueIdByDiscordId(userId);
	        BreadMaker bread = Synergy.getBread(uniqueId);
        	bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
        }
    }
    
    @Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
       // String userId = event.getUser().getId();
    }
    
    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-missing.enabled")) {
	        String userId = event.getUser().getId();
	        UUID uniqueId = Discord.getUniqueIdByDiscordId(userId);
	        BreadMaker bread = Synergy.getBread(uniqueId);
        	bread.kick(Synergy.getConfig().getString("discord.kick-player.if-missing.message"));
        }
    }
    
    @Override
    public void onGuildMemberUpdateTimeOut(@NotNull GuildMemberUpdateTimeOutEvent event) {
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-muted.enabled")) {
	        String userId = event.getUser().getId();
	        if (event.getNewTimeOutEnd() != null) {
		        UUID uniqueId = Discord.getUniqueIdByDiscordId(userId);
		        BreadMaker bread = Synergy.getBread(uniqueId);
	        	bread.kick(Synergy.getConfig().getString("discord.kick-player.if-muted.message"));
	        } else {
	            // 
	        }
        }
    }
}