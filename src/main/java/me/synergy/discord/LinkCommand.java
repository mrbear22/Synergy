package me.synergy.discord;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Translation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class LinkCommand extends ListenerAdapter implements SynergyListener {

	public LinkCommand() {
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
	
    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
    	
    	if (event.getIdentifier().equals("make-discord-link")) {
    		UUID uuid = event.getPlayerUniqueId();
    		String discordTag = event.getOption("tag").getAsString();
    		makeDiscordLink(uuid, discordTag);
    	}

    	if (event.getIdentifier().equals("confirm-discord-link")) {
    		UUID uuid = event.getPlayerUniqueId();
    		confirmDiscordLink(uuid);
    	}

        if (event.getIdentifier().equals("create-discord-link")) {
        	UUID uuid = event.getPlayerUniqueId();
            String discordId = event.getOption("id").getAsString();
            createDiscordLink(uuid, discordId);
        }

    	if (event.getIdentifier().equals("remove-discord-link")) {
    		UUID uuid = event.getPlayerUniqueId();
    		removeDiscordLink(uuid);
    	}
    	
    	if (event.getIdentifier().equals("retrieve-users-tags") && !event.getOption("tags").isSet()) {
            Discord.getUsersTagsCache().clear();
            Synergy.getDiscord().getGuilds().forEach(guild ->
                guild.getMembers().stream()
                    .filter(member -> !member.getUser().isBot())
                    .forEach(member -> Discord.getUsersTagsCache().add(member.getUser().getName()))
            );
        	Synergy.createSynergyEvent("retrieve-users-tags").setOption("tags", String.join(",", Discord.getUsersTagsCache())).send();
    	}
    }
	
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] id = event.getComponentId().split(":");
        String authorId = id[0];
        String type = id[1];
        if (!authorId.equals(event.getUser().getId())) {
            return;
        }
        event.deferEdit().queue();
        switch (type) {
            case "confirm":
            	if (Discord.getUniqueIdByDiscordId(event.getUser().getId()) == null) {
            		UUID uuid = UUID.fromString(id[2]);
            		createDiscordLink(uuid, authorId);
            		BreadMaker bread = Synergy.getBread(uuid);
	                event.getUser().openPrivateChannel().complete().sendMessage(Synergy.translate("<lang>synergy-discord-link-success</lang>", Translation.getDefaultLanguage()).getStripped().replace("%ACCOUNT%", bread.getName())).queue();
            	}
                break;
        }
    }
	
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    	if (!event.getName().toLowerCase().equals("link")) {
    		return;
    	}
    	UUID uuid = Discord.getUniqueIdByDiscordId(event.getMember().getId());
    	if (uuid != null) {
            event.replyEmbeds(Discord.warning(
                    Synergy.translate("<lang>synergy-link-discord-already-linked</lang>", Translation.getDefaultLanguage())
                           .getStripped()
                           .replace("%ACCOUNT%", Synergy.getBread(uuid).getName())
                )).setEphemeral(true).queue();
            return;
    	}
        TextInput subject = TextInput.create("username", Synergy.translate("<lang>synergy-link-minecraft-your-username</lang>", Translation.getDefaultLanguage()).getStripped(), TextInputStyle.SHORT).setPlaceholder("Steve").setMinLength(3).setMaxLength(28).build();
        Modal modal = Modal.create("minecraftlink", Synergy.translate("<lang>synergy-link-minecraft-title</lang>", Translation.getDefaultLanguage()).getStripped()).addComponents(new LayoutComponent[] {
            ActionRow.of(new ItemComponent[] {
                subject
            })
        }).build();
        event.replyModal(modal).queue();
    }
    
    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (!event.getModalId().equals("minecraftlink")) return;

        String username = event.getValue("username").getAsString();
        UUID uuid = Synergy.getOfflineUniqueId(username);

        if (uuid == null) {
            String unknownUser = Synergy.translate("<lang>synergy-unknown-user</lang>", Translation.getDefaultLanguage()).getStripped();
            event.replyEmbeds(Discord.warning(
                Synergy.translate("<lang>synergy-player-doesnt-exist</lang>", Translation.getDefaultLanguage())
                       .getStripped()
                       .replace("%ACCOUNT%", unknownUser)
            )).setEphemeral(true).queue();
            return;
        }

        String discordId = Discord.getDiscordIdByUniqueId(uuid);
        User linkedUser = (discordId != null) ? Synergy.getDiscord().getUserById(discordId) : null;
        String linkedUserName = (linkedUser != null) ? linkedUser.getEffectiveName() : Synergy.translate("<lang>synergy-unknown-user</lang>", Translation.getDefaultLanguage()).getStripped();

        if (discordId != null) {
            event.replyEmbeds(Discord.warning(
                Synergy.translate("<lang>synergy-link-minecraft-already-linked</lang>", Translation.getDefaultLanguage())
                       .getStripped()
                       .replace("%ACCOUNT%", linkedUserName)
            )).setEphemeral(true).queue();
            return;
        }

        UUID existingAccount = Discord.getUniqueIdByDiscordId(event.getUser().getId());
        if (existingAccount != null) {
            String existingName = Synergy.getBread(existingAccount).getName();
            event.replyEmbeds(Discord.warning(
                Synergy.translate("<lang>synergy-link-discord-already-linked</lang>", Translation.getDefaultLanguage())
                       .getStripped()
                       .replace("%ACCOUNT%", existingName)
            )).setEphemeral(true).queue();
            return;
        }

        BreadMaker bread = new BreadMaker(uuid);
        bread.setData("confirm-discord", event.getUser().getId());
        
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            bread.setData("confirm-discord", null);
            scheduler.shutdown();
        }, 10, TimeUnit.MINUTES);

        event.replyEmbeds(Discord.info(
            Synergy.translate("<lang>synergy-link-minecraft-confirmation</lang>", Translation.getDefaultLanguage())
                   .getStripped()
        )).setEphemeral(true).queue();

        bread.sendMessage(
            Translation.translate("<lang>synergy-link-discord-confirmation</lang>", bread.getLanguage())
                      .replace("%ACCOUNT%", event.getUser().getEffectiveName())
        );
    }

    
    public void removeDiscordLink(UUID uuid) {
        BreadMaker bread = new BreadMaker(uuid);
        if (bread.getData("discord", false).isSet()) {
            bread.setData("discord", null);
            bread.sendMessage("<lang>synergy-link-minecraft-unlinked</lang>");
            return;
        }
        bread.sendMessage("<lang>synergy-you-have-no-linked-accounts</lang>");
    }


    public void createDiscordLink(UUID uuid, String discordId) {
    	BreadMaker bread = new BreadMaker(uuid);
    	bread.setData("discord", discordId);
		String account = Synergy.getDiscord().getUserById(discordId).getEffectiveName();
    	bread.sendMessage(Translation.translate("<lang>synergy-discord-link-success</lang>", bread.getLanguage()).replace("%ACCOUNT%", account));
    //	RolesDiscordListener.addVerifiedRole(discordId);
    }

    public void confirmDiscordLink(UUID uuid) {
    	BreadMaker bread = new BreadMaker(uuid);
		if (bread.getData("confirm-discord").isSet()) {
	    	String discordid = bread.getData("confirm-discord").getAsString();
	    	createDiscordLink(uuid, discordid);
	    	bread.setData("confirm-discord", null);
		} else {
			bread.sendMessage("<lang>synergy-confirmation-nothing-to-confirm</lang>");
		}
    }

    public void makeDiscordLink(UUID uuid, String discordTag) {
    	BreadMaker bread = new BreadMaker(uuid);

       	if (Discord.getDiscordIdByUniqueId(uuid) != null) {
    		String account = Synergy.getDiscord().getUserById(Discord.getDiscordIdByUniqueId(uuid)).getEffectiveName();
    		bread.sendMessage(Translation.translate("<lang>synergy-link-discord-already-linked</lang>", bread.getLanguage()).replace("%ACCOUNT%", account));
    		return;
    	}

        try {
        	for (Guild guild : Synergy.getDiscord().getGuilds()) {
        	    for (Member member : guild.getMembers()) {
        	        if (member.getUser().getName().equalsIgnoreCase(discordTag)) {
        	            User user = member.getUser();

        	            if (Discord.getUniqueIdByDiscordId(user.getId()) != null) {
        	            	bread.sendMessage(Translation.translate("<lang>synergy-link-minecraft-already-linked</lang>", bread.getLanguage()).replace("%ACCOUNT%", bread.getName()));
        	                return;
        	            }

        	            PrivateChannel privateChannel = user.openPrivateChannel().complete();
        	            String message = Synergy.translate("<lang>synergy-discord-confirm-link</lang>", bread.getLanguage()).getStripped().replace("%ACCOUNT%", bread.getName());

        	            MessageHistory history = privateChannel.getHistory();
        	            Message lastMessage = history.retrievePast(1).complete().size() == 0 ? null : history.retrievePast(1).complete().get(0);

        	            if (lastMessage == null || !lastMessage.getContentRaw().equals(message)) {
        	                if (privateChannel.canTalk()) {
        	                    privateChannel.sendMessage(message)
        	                            .addActionRow(
        	                                    Button.success(user.getId() + ":confirm:" + uuid, Synergy.translate("<lang>synergy-confirm-action</lang>", bread.getLanguage()).getStripped()))
        	                            .queue();
        	                    bread.sendMessage(Translation.translate("<lang>synergy-discord-link-check-pm</lang>", bread.getLanguage()).replace("%INVITE%", Synergy.getConfig().getString("discord.invite-link")));
        	                } else {
        	                	bread.sendMessage(Translation.translate("<lang>synergy-discord-use-link-cmd</lang>", bread.getLanguage()).replace("%INVITE%", Synergy.getConfig().getString("discord.invite-link")));
        	                }
        	            } else {
        	            	bread.sendMessage(Translation.translate("<lang>synergy-discord-use-link-cmd</lang>", bread.getLanguage()).replace("%INVITE%", Synergy.getConfig().getString("discord.invite-link")));
        	            }
        	            return;
        	        }
        	    }
        	}
        } catch (Exception c) {
        	c.printStackTrace();
        	bread.sendMessage(Translation.translate("<lang>synergy-discord-use-link-cmd</lang>", bread.getLanguage()).replace("%INVITE%", Synergy.getConfig().getString("discord.invite-link")));
        }
    }
    
}
