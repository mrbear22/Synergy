package me.synergy.discord;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.synergy.brains.Synergy;
import me.synergy.integrations.PlaceholdersAPI;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Translation;
import me.synergy.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Discord {

    private static JDA JDA;
    ScheduledExecutorService REPEATING_TASK = Executors.newSingleThreadScheduledExecutor();
    public static Set<String> USERS_TAGS_CACHE = new CopyOnWriteArraySet<>();
    private static long LAST_CACHE_UPDATE = 0;
    private static long CACHE_UPDATE_INTERVAL = 60000;
    
    private static final GatewayIntent[] INTENTS = new GatewayIntent[] {
        GatewayIntent.SCHEDULED_EVENTS, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES
    };

    public void initialize() {
        try {
            if (!Synergy.getConfig().getBoolean("discord.enabled")) {
                return;
            }

            JDABuilder bot = botBuilder();
            
            bot.addEventListeners(new ListCommand());
            bot.addEventListeners(new LinkCommand());
            bot.addEventListeners(new VoteCommand());
            bot.addEventListeners(new RolesHandler());
            bot.addEventListeners(new ChatHandler());
            bot.addEventListeners(new EmbedCommand());
            bot.addEventListeners(new MembersHandler());
      
            JDA = bot.build();
            
            updateCommands();

            activityStatus();

            Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
        } catch (Exception exception) {
            Synergy.getLogger().error(String.valueOf(getClass().getSimpleName()) + " module failed to initialize: " + exception.getMessage());
        	exception.printStackTrace();
        }
    }
    
    private void updateCommands() {
    	
        CommandListUpdateAction commands = Synergy.getDiscord().updateCommands();
        
        commands.addCommands(new CommandData[] {
                Commands.slash("list", Synergy.translate("<lang>synergy-online-players-list</lang>", Translation.getDefaultLanguage()).getStripped())
                    .setGuildOnly(true)
            });
        commands.addCommands(new CommandData[] {
                Commands.slash("vote", Synergy.translate("<lang>synergy-vote-for-server</lang>", Translation.getDefaultLanguage()).getStripped())
                    .setGuildOnly(true)
            });
        commands.addCommands(new CommandData[] {
                Commands.slash("link", Synergy.translate("<lang>synergy-link-minecraft-title</lang>", Translation.getDefaultLanguage()).getStripped())
                    .setGuildOnly(true)
            });
        commands.addCommands(new CommandData[] {
                Commands.slash("embed", Synergy.translate("<lang>synergy-discord-embed-new</lang>", Translation.getDefaultLanguage()).getStripped())
                    .addOptions(new OptionData[] {
                        (new OptionData(OptionType.CHANNEL, "channel", "Channel ID")).setRequired(true)
                    })
                    .addOptions(new OptionData[] {
                        (new OptionData(OptionType.STRING, "message", "Message ID (edit a message that has already been sent)"))
                    })
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(new Permission[] {
                        Permission.MESSAGE_MANAGE
                    }))
            });
        commands.addCommands(new CommandData[] {
                Commands.slash("post", Synergy.translate("<lang>synergy-create-post</lang>", Translation.getDefaultLanguage()).getStripped())
                    .addOptions(new OptionData[] {
                        (new OptionData(OptionType.STRING, "title", "Title")).setRequired(true)
                    })
                    .addOptions(new OptionData[] {
                        (new OptionData(OptionType.STRING, "text", "Text")).setRequired(true)
                    })
                    .addOptions(new OptionData[] {
                        new OptionData(OptionType.STRING, "author", "Author")
                    })
                    .addOptions(new OptionData[] {
                        new OptionData(OptionType.CHANNEL, "channel", "Channel")
                    })
                    .addOptions(new OptionData[] {
                        new OptionData(OptionType.STRING, "image", "Image url")
                    })
                    .addOptions(new OptionData[] {
                        new OptionData(OptionType.STRING, "color", "#Color")
                    })
                    .addOptions(new OptionData[] {
                        new OptionData(OptionType.STRING, "thumbnail", "Image url")
                    })
                    .addOptions(new OptionData[] {
                        new OptionData(OptionType.MENTIONABLE, "mention", "Mention")
                    })
                    .addOptions(new OptionData[] {
                        new OptionData(OptionType.STRING, "edit", "Message ID (edit a message that has already been sent)")
                    })
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(new Permission[] {
                        Permission.MESSAGE_MANAGE
                    }))
            });
        commands.queue();
    }
    
    private JDABuilder botBuilder() {
       return JDABuilder.create(Synergy.getConfig().getString("discord.bot-token"), Arrays.asList(INTENTS))
                .enableCache(CacheFlag.MEMBER_OVERRIDES, new CacheFlag[] {
                    CacheFlag.VOICE_STATE
                })
                .disableCache(CacheFlag.ACTIVITY, new CacheFlag[] {
                    CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER
                })
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.customStatus(Synergy.getConfig().getStringList("discord.activities").get(0)))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setBulkDeleteSplittingEnabled(true);
    }
    
    public void shutdown() {
        if (getJda() != null) {
            for (Object listener : getJda().getRegisteredListeners()) {
                getJda().removeEventListener(listener);
            }
            getJda().shutdownNow();
        }
    }
    
    private void activityStatus() {
        if (Synergy.getConfig().getStringList("discord.activities").size() > 1) {
        	REPEATING_TASK.scheduleAtFixedRate(() -> {
                long currentTimeSeconds = System.currentTimeMillis() / 1000;
                int index = (int)(currentTimeSeconds % Synergy.getConfig().getStringList("discord.activities").size());
                String customStatusText = Synergy.getConfig().getStringList("discord.activities").get(index);
                customStatusText = PlaceholdersAPI.processPlaceholders(null, customStatusText);
                customStatusText = customStatusText.replace("%online%", String.valueOf(Utils.getPlayers().size()));
                Discord.JDA.getPresence().setActivity(Activity.customStatus(customStatusText));
            }, 0, 60, TimeUnit.SECONDS);
        }
    }
    
    public static Guild getGuild() {
    	return JDA.getGuildById(Synergy.getConfig().getString("discord.guild-id"));
    }
    
    public static Member getMember(BreadMaker bread) {
        if (!bread.getData("discord").isSet()) {
            return null;
        }
        try {
            return getGuild().getMemberById(bread.getData("discord").getAsString());
        } catch (Exception e) {
            Synergy.getLogger().warning("Error while receiving a user: " + e.getMessage());
            return null;
        }
    }
    
    public static Set<String> getUsersTagsCache() {
        if (System.currentTimeMillis() - LAST_CACHE_UPDATE >= CACHE_UPDATE_INTERVAL) {
        	LAST_CACHE_UPDATE = System.currentTimeMillis();
        	Synergy.createSynergyEvent("retrieve-users-tags").send();
        }
        return USERS_TAGS_CACHE;
    }

    public static String getDiscordIdByUniqueId(UUID uuid) {
    	BreadMaker bread = Synergy.getBread(uuid);
    	String discord = bread.getData("discord").getAsString();
        return discord;
    }
    
    public static UUID getUniqueIdByDiscordId(String id) {
    	return Synergy.findUserUUID("discord", id);
    }
    
    public static String getBotName() {
        return Synergy.getConfig().getString("discord.gpt-bot.name");
    }

    public static MessageEmbed info(String message) {
    	EmbedBuilder embed = new EmbedBuilder();
    	embed.setColor(Color.decode("#3498db"));
    	embed.setTitle(message);
    	return embed.build();
    }

    public static MessageEmbed warning(String message) {
    	EmbedBuilder embed = new EmbedBuilder();
    	embed.setColor(Color.decode("#f39c12"));
    	embed.setTitle(message);
    	return embed.build();
    }
    
    public static boolean isBanned(BreadMaker bread) {
        if (!bread.getData("discord").isSet()) {
            return false;
        }
        try {
            List<Ban> bans = getGuild().retrieveBanList().complete();
            String userId = bread.getData("discord").getAsString();
            return bans.stream().anyMatch(ban -> ban.getUser().getId().equals(userId));
        } catch (PermissionException e) {
            Synergy.getLogger().warning("Insufficient privileges to receive the ban list: " + e.getMessage());
        } catch (Exception e) {
            Synergy.getLogger().warning("Error during ban check: " + e.getMessage());
        }
        return false;
    }

    public static boolean isMissing(BreadMaker bread) {
        if (!bread.getData("discord").isSet()) {
            return false;
        }
        Member member = getMember(bread);
        return member == null;
    }

    public static boolean isMuted(BreadMaker bread) {
        Member member = getMember(bread);
        if (member == null) return false;
        try {
            OffsetDateTime timeoutEnd = member.getTimeOutEnd();
            return timeoutEnd != null && timeoutEnd.isAfter(OffsetDateTime.now());
        } catch (PermissionException e) {
            Synergy.getLogger().warning("Insufficient rights to check mute: " + e.getMessage());
        } catch (Exception e) {
            Synergy.getLogger().warning("Error during mute check: " + e.getMessage());
        }
        return false;
    }
    
    public JDA getJda() {
        return JDA;
    }

}