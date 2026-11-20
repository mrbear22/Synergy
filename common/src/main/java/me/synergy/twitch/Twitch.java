package me.synergy.twitch;

import java.util.UUID;

import me.synergy.anotations.SynergyHandler;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.StreamRewards;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.modules.Module;
import me.synergy.objects.BreadMaker;

public class Twitch implements Module {

    @Override public String toggle() { return "twitch.enabled"; }

    @Override
    public void defaults() {
        Locales.addDefault("twitch.error.credentials", "<danger>Invalid Twitch credentials!");
        Locales.addDefault("twitch.error.connection", "<danger>Failed to connect to Twitch!");
        Locales.addDefault("twitch.error.input", "Your input doesn't match the required format");
        Locales.addDefault("twitch.reward.untitled", "<danger>Reward title can't be empty!");
        Locales.addDefault("twitch.error.user", "<danger>Failed to get your Twitch user ID!");
        Locales.addDefault("twitch.error.offline", "<danger>Twitch isn't connected for your channel!");
        Locales.addDefault("twitch.reward.created", "<success>Reward created!");
        Locales.addDefault("twitch.reward.error.create", "<danger>Failed to create the reward!");
        Locales.addDefault("twitch.reward.none", "<danger>Your channel has no rewards!");
        Locales.addDefault("twitch.reward.missing", "<danger>Reward not found!");
        Locales.addDefault("twitch.reward.removed", "<success>Reward removed!");
        Locales.addDefault("twitch.reward.error.remove", "<danger>Failed to remove the reward!");
        Config.addDefault("twitch.enabled", false);
        if (Synergy.isHub()) Config.addDefault("twitch.client-id", "client-id");
        if (!Synergy.isProxy() && Config.getConfigurationSection("twitch.rewards").isEmpty()) {
            Config.addDefault("twitch.rewards.wither.title", "Wither");
            Config.addDefault("twitch.rewards.wither.description", "Spawn a Wither");
            Config.addDefault("twitch.rewards.wither.cost", 1000);
            Config.addDefault("twitch.rewards.wither.input-required", false);
            Config.addDefault("twitch.rewards.wither.input-regex", "[a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9]");
            Config.addDefault("twitch.rewards.wither.commands", new String[] {"execute at %streamer_name% run summon minecraft:wither ~ ~2 ~", "execute at %streamer_name% run title @a[distance=..500] title {\"text\":\"%viewer_name% has spawned a Wither\"}"});
        }

    }

    @Override
    public void start() {
        Synergy.getEventManager().registerEvents(Twitch.class);
    }
    
    /** Stream connections live on the hub only: it joins and leaves them as the player enters or leaves the network. */
    @Override
    public void join(BreadMaker bread) throws Exception {
        String channel = bread.getData("twitch-username").getAsString();
        if (Synergy.isHub() && channel != null) getConnectionManager().connect(channel, bread.getData("twitch-token").getAsString());
    }

    @Override
    public void quit(BreadMaker bread) throws Exception {
        String channel = bread.getData("twitch-username").getAsString();
        if (Synergy.isHub() && channel != null) getConnectionManager().disconnect(channel);
    }

    public static ConnectionManager getConnectionManager() {
    	return new ConnectionManager();
    }
    
    public static LinksManager getLinksManager() {
    	return new LinksManager();
    }
    
    public static RewardManager getRewardsManager(String channelName, String accessToken) {
    	return new RewardManager(channelName, accessToken);
    }
    
    @SynergyHandler({"make-twitch-link", "remove-twitch-link", "create-twitch-reward", "remove-twitch-reward", "send-twitch-message"})
    public static void onControl(SynergyEvent event) {
        UUID uuid = event.getPlayerUniqueId();
        if (!Synergy.isHub() || uuid == null || Config.getString("twitch.client-id").equals("client-id")) return;
        BreadMaker bread = event.getBread();
        String channel = bread.getData("twitch-username").getAsString(), token = bread.getData("twitch-token").getAsString();
        try {
            switch (event.getIdentifier()) {
                case "make-twitch-link" -> getLinksManager().linkAccount(uuid, event.getOption("tag").getAsString(), event.getOption("token").getAsString());
                case "remove-twitch-link" -> getLinksManager().unlinkAccount(uuid);
                case "create-twitch-reward" -> {
                    if (channel != null && token != null) getRewardsManager(channel, token).create(event.getOption("title").getAsString(), event.getOption("cost").getAsInteger(),
                        event.getOption("description").getAsString(), event.getOption("input-required").getAsBoolean());
                }
                case "remove-twitch-reward" -> {
                    if (channel != null && token != null) getRewardsManager(channel, token).delete(event.getOption("title").getAsString());
                }
                case "send-twitch-message" -> {
                    if (channel != null) ChatHandler.sendMessage(channel, event.getOption("message").getAsString());
                }
            }
        } catch (Exception e) {
            bread.sendMessage(e.getMessage());
        }
    }

    @SynergyHandler("twitch-reward-redeemed")
    public static void onRedeem(SynergyEvent event) {
        BreadMaker bread = event.getBread();
        if (Synergy.isProxy() || !bread.isOnline()) return;
        String title = event.getOption("reward-title").getAsString(), viewer = event.getOption("viewer-name").getAsString(), input = event.getOption("viewer-input").getAsString();
        String channel = event.getOption("channel-name").getAsString();
        Synergy.getLogger().info("[Twitch] [" + channel + "] " + viewer + " redeemed: " + title);
        StreamRewards.find("twitch", path -> title.equalsIgnoreCase(Config.getString(path + ".title"))).ifPresent(key -> {
            if (!StreamRewards.run("twitch", key, bread, input, "%viewer_name%", viewer, "%reward_title%", title, "%viewer_input%", input, "%reward_id%", event.getOption("reward-id").getAsString()))
                Synergy.event("send-twitch-message").toProxy().setPlayerUniqueId(bread.getUniqueId()).setOption("message", "<locale:twitch.error.input>").send();
        });
    }

    @Override
    public void stop() {
    	ConnectionManager.channelClients.forEach((channelName, client) -> {
            try {
                if (client != null) {
                    client.getChat().leaveChannel(channelName);
                    if (client.getEventSocket() != null) {
                        client.getEventSocket().close();
                    }
                    client.close();
                }
            } catch (Exception e) {
                Synergy.getLogger().error("Disconnect error " + channelName + ": " + e.getMessage());
            }
        });
    	ConnectionManager.channelClients.clear();
    }

}