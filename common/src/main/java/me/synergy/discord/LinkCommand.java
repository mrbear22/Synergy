package me.synergy.discord;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class LinkCommand extends ListenerAdapter implements SynergyListener {

    public LinkCommand() {
        Synergy.getEventManager().registerEvents(this);
    }

    @SynergyHandler("discord-link")
    public void onLink(SynergyEvent event) {
        String tag = event.getOption("tag").getAsString();
        if (tag == null) request(event.getPlayerUniqueId());
        else invite(event.getPlayerUniqueId(), tag);
    }

    @SynergyHandler("discord-confirm")
    public void onConfirm(SynergyEvent event) {
        BreadMaker bread = event.getBread();
        String discordId = bread.getData("confirm-discord").getAsString();
        if (discordId == null) {
            LocaleBuilder.of("discord.link.request.missing").send(bread);
            return;
        }
        bread.setData("confirm-discord", null);
        link(bread.getUniqueId(), discordId);
    }

    @SynergyHandler("discord-unlink")
    public void onUnlink(SynergyEvent event) {
        BreadMaker bread = event.getBread();
        if (!bread.getData("discord").isSet()) LocaleBuilder.of("discord.link.missing").placeholder("command", "/discord link").send(bread);
        else unlink(bread.getUniqueId());
    }

    @SynergyHandler("discord-tags")
    public void onTags(SynergyEvent event) {
        event.reply().setOption("tags", Synergy.getDiscord().getGuilds().stream().flatMap(g -> g.getMembers().stream())
            .map(Member::getUser).filter(u -> !u.isBot()).map(User::getName).distinct().sorted().toList()).send();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] id = event.getComponentId().split(":");
        if (id.length < 3 || !id[1].equals("confirm") || !id[0].equals(event.getUser().getId())) return;
        event.deferEdit().queue();
        if (Discord.getUniqueIdByDiscordId(id[0]) != null) return;
        UUID uuid = UUID.fromString(id[2]);
        link(uuid, id[0]);
        String text = LocaleBuilder.of("discord.link.done").placeholder("account", Synergy.getBread(uuid).getName()).stripped(Discord.language(id[0]));
        event.getUser().openPrivateChannel().flatMap(channel -> channel.sendMessage(text)).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        String[] parts = event.getMessage().getContentRaw().trim().split("\\s+", 2);
        Consumer<String> reply = text -> event.getMessage().reply(text).queue();
        if (parts[0].equalsIgnoreCase("/unlink")) unlink(event.getAuthor(), reply);
        else if (parts[0].equalsIgnoreCase("/link")) {
            if (parts.length < 2) reply.accept(text("discord.link.form.username", event.getAuthor()));
            else request(event.getAuthor(), parts[1], reply);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Consumer<String> reply = text -> event.replyEmbeds(Discord.info(text)).setEphemeral(true).queue();
        if (event.getName().equalsIgnoreCase("unlink")) unlink(event.getUser(), reply);
        if (!event.getName().equalsIgnoreCase("link")) return;
        String nickname = event.getOption("nickname") != null ? event.getOption("nickname").getAsString().trim() : "";
        if (!nickname.isEmpty()) request(event.getUser(), nickname, reply);
        else if (!event.getChannelType().isGuild()) reply.accept(text("discord.link.required", event.getUser()));
        else if (!linked(event.getUser(), reply)) {
            TextInput username = TextInput.create("username", text("discord.link.form.username", event.getUser()), TextInputStyle.SHORT).setPlaceholder("Steve").setMinLength(3).setMaxLength(28).build();
            event.replyModal(Modal.create("minecraftlink", text("discord.link.form.title", event.getUser())).addComponents(ActionRow.of(username)).build()).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().equals("minecraftlink"))
            request(event.getUser(), event.getValue("username").getAsString(), text -> event.replyEmbeds(Discord.info(text)).setEphemeral(true).queue());
    }

    private static String text(String key, User user) {
        return LocaleBuilder.of(key).stripped(Discord.language(user.getId()));
    }

    private static boolean linked(User user, Consumer<String> reply) {
        UUID linked = Discord.getUniqueIdByDiscordId(user.getId());
        if (linked != null) reply.accept(LocaleBuilder.of("discord.link.exists").placeholder("account", Synergy.getBread(linked).getName()).placeholder("command", "/unlink").stripped(Discord.language(user.getId())));
        return linked != null;
    }

    private static void unlink(User user, Consumer<String> reply) {
        UUID uuid = Discord.getUniqueIdByDiscordId(user.getId());
        if (uuid == null) reply.accept(LocaleBuilder.of("discord.link.missing").placeholder("command", "/link").stripped(Discord.language(user.getId())));
        else {
            reply.accept(text("discord.unlink.done", user));
            unlink(uuid);
        }
    }

    private static void request(User user, String username, Consumer<String> reply) {
        if (linked(user, reply)) return;
        UUID uuid = DataManager.findUuidByName(username);
        if (uuid == null) {
            reply.accept(text("discord.link.player.unknown", user));
            return;
        }
        String linked = Discord.getDiscordIdByUniqueId(uuid);
        if (linked != null) {
            reply.accept(LocaleBuilder.of("discord.link.exists").placeholder("account", Discord.name(linked)).placeholder("command", "/unlink").stripped(Discord.language(user.getId())));
            return;
        }
        Synergy.getBread(uuid).setData("confirm-discord", user.getId(), Duration.ofMinutes(10));
        reply.accept(text("discord.link.request.sent", user));
        LocaleBuilder.of("discord.link.confirm").placeholder("account", user.getEffectiveName()).send(uuid);
    }

    public static void link(UUID uuid, String discordId) {
        Synergy.getBread(uuid).setData("discord", discordId);
        LocaleBuilder.of("discord.link.done").placeholder("account", Discord.name(discordId)).send(uuid);
        RolesHandler.sync(uuid);
    }

    private static void unlink(UUID uuid) {
        Synergy.getBread(uuid).setData("discord", null);
        LocaleBuilder.of("discord.unlink.done").send(uuid);
        RolesHandler.sync(uuid);
        if (Config.getBoolean("discord.kick-player.if-has-no-link.enabled")) Synergy.getBread(uuid).kick(Config.getString("discord.kick-player.if-has-no-link.message"));
    }

    private static void request(UUID uuid) {
        String linked = Discord.getDiscordIdByUniqueId(uuid);
        if (linked != null) LocaleBuilder.of("discord.link.exists").placeholder("account", Discord.name(linked)).placeholder("command", "/discord unlink").send(uuid);
        else if (DiscordOAuth.isEnabled()) LocaleBuilder.of("discord.link.oauth.url").placeholder("url", DiscordOAuth.createAuthorizationUrl(uuid)).send(uuid);
        else LocaleBuilder.of("discord.link.guide").placeholder("invite", Config.getString("discord.invite-link")).send(uuid);
    }

    private static void invite(UUID uuid, String tag) {
        BreadMaker bread = Synergy.getBread(uuid);
        String linked = Discord.getDiscordIdByUniqueId(uuid);
        if (linked != null) {
            LocaleBuilder.of("discord.link.exists").placeholder("account", Discord.name(linked)).placeholder("command", "/discord unlink").send(bread);
            return;
        }
        User user = Synergy.getDiscord().getGuilds().stream().flatMap(g -> g.getMembers().stream()).map(Member::getUser)
            .filter(u -> u.getName().equalsIgnoreCase(tag)).findFirst().orElse(null);
        UUID owner = user == null ? null : Discord.getUniqueIdByDiscordId(user.getId());
        if (user == null) request(uuid);
        else if (owner != null) LocaleBuilder.of("discord.link.exists").placeholder("account", Synergy.getBread(owner).getName()).placeholder("command", "/discord unlink").send(bread);
        else if (bread.cooldown("discord-confirm-dm", 60)) LocaleBuilder.of("discord.link.dm.sent").send(bread);
        else {
            String language = bread.getLanguage();
            Button confirm = Button.success(user.getId() + ":confirm:" + uuid, LocaleBuilder.of("discord.link.dm.button").stripped(language));
            user.openPrivateChannel().flatMap(channel -> channel.sendMessage(LocaleBuilder.of("discord.link.dm.text").placeholder("account", bread.getName()).stripped(language)).addActionRow(confirm))
                .queue(sent -> LocaleBuilder.of("discord.link.dm.sent").send(bread), error -> request(uuid));
        }
    }
}
