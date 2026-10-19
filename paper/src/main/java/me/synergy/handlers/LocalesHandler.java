package me.synergy.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDeathCombatEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMerchantOffers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.synergy.brains.Spigot;
import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class LocalesHandler {

    private final List<PacketListenerAbstract> listeners = new ArrayList<>();

    public void initialize() {
        Config.addDefault("localizations.modules.chat", true);
        Config.addDefault("localizations.modules.scoreboard", true);
        Config.addDefault("localizations.modules.entities", true);
        Config.addDefault("localizations.modules.merchant", false);
        Config.addDefault("localizations.modules.items", false);

        if (!Config.getBoolean("localizations.enabled")) return;

        try {
            registerPacketListeners();
        } catch (Exception e) {
            Synergy.getLogger().error("Packet localization failed to start: " + e, e);
        }
    }

    public void shutdown() {
        if (listeners.isEmpty()) return;

        var eventManager = PacketEvents.getAPI().getEventManager();
        listeners.forEach(eventManager::unregisterListener);
        listeners.clear();

        Synergy.getLogger().info(getClass().getSimpleName() + " module has been shut down!");
    }

    private void registerPacketListeners() {
        var eventManager = PacketEvents.getAPI().getEventManager();

        if (Config.getBoolean("localizations.modules.chat")) {
            eventManager.registerListener(createListener(PacketListenerPriority.HIGH, this::processComponents,
                PacketType.Play.Server.SYSTEM_CHAT_MESSAGE));

            eventManager.registerListener(createListener(PacketListenerPriority.MONITOR, this::processComponents,
                PacketType.Play.Server.SET_TITLE_TEXT, PacketType.Play.Server.SET_TITLE_SUBTITLE,
                PacketType.Play.Server.ACTION_BAR, PacketType.Play.Server.BOSS_BAR,
                PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER,
                PacketType.Play.Server.TEAMS, PacketType.Play.Server.OPEN_WINDOW,
                PacketType.Play.Server.DISCONNECT));

            eventManager.registerListener(createListener(PacketListenerPriority.MONITOR, this::processDeathScreen,
                PacketType.Play.Server.DEATH_COMBAT_EVENT));
        }

        if (Config.getBoolean("localizations.modules.scoreboard")) {
            eventManager.registerListener(createListener(PacketListenerPriority.MONITOR, this::processScoreboardObjective,
                PacketType.Play.Server.SCOREBOARD_OBJECTIVE));
        }

        if (Config.getBoolean("localizations.modules.items")) {
            eventManager.registerListener(createListener(PacketListenerPriority.NORMAL, this::processInventoryItems,
                PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.SET_SLOT));
        }

        if (Config.getBoolean("localizations.modules.entities")) {
            eventManager.registerListener(createListener(PacketListenerPriority.NORMAL, this::processEntityMetadata,
                PacketType.Play.Server.ENTITY_METADATA));
        }

        if (Config.getBoolean("localizations.modules.merchant")) {
            eventManager.registerListener(createListener(PacketListenerPriority.NORMAL, this::processMerchantOffers,
                PacketType.Play.Server.MERCHANT_OFFERS));
        }
    }

    private PacketListenerAbstract createListener(PacketListenerPriority priority, PacketProcessor processor, PacketTypeCommon... types) {
        var typeSet = Set.of(types);
        var listener = new PacketListenerAbstract(priority) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (!typeSet.contains(event.getPacketType())) return;
                try {
                    processor.process(event);
                } catch (Exception e) {
                    Synergy.getLogger().error("Error processing packet " + event.getPacketType() + ": " + e.getMessage());
                }
            }
        };
        listeners.add(listener);
        return listener;
    }

    private void processComponents(PacketSendEvent event) {
        var player = (Player) event.getPlayer();
        var bread = Synergy.getBread(event.getUser().getUUID());
        var type = event.getPacketType();

        if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            var packet = new WrapperPlayServerSystemChatMessage(event);
            translateDelayable(event, player, bread, packet.getMessage(), packet::setMessage,
                translated -> new WrapperPlayServerSystemChatMessage(packet.isOverlay(), translated));
            return;
        }

        if (type == PacketType.Play.Server.SET_TITLE_TEXT) {
            var packet = new WrapperPlayServerSetTitleText(event);
            translateSimple(event, bread, packet.getTitle(), packet::setTitle);
            return;
        }

        if (type == PacketType.Play.Server.SET_TITLE_SUBTITLE) {
            var packet = new WrapperPlayServerSetTitleSubtitle(event);
            translateSimple(event, bread, packet.getSubtitle(), packet::setSubtitle);
            return;
        }

        if (type == PacketType.Play.Server.ACTION_BAR) {
            var packet = new WrapperPlayServerActionBar(event);
            translateSimple(event, bread, packet.getActionBarText(), packet::setActionBarText);
            return;
        }

        if (type == PacketType.Play.Server.BOSS_BAR) {
            var packet = new WrapperPlayServerBossBar(event);
            if (packet.getAction() == WrapperPlayServerBossBar.Action.ADD
                || packet.getAction() == WrapperPlayServerBossBar.Action.UPDATE_TITLE) {
                translateSimple(event, bread, packet.getTitle(), packet::setTitle);
            }
            return;
        }

        if (type == PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER) {
            var packet = new WrapperPlayServerPlayerListHeaderAndFooter(event);
            translateSimple(event, bread, packet.getHeader(), packet::setHeader);
            translateSimple(event, bread, packet.getFooter(), packet::setFooter);
            return;
        }

        if (type == PacketType.Play.Server.TEAMS) {
            var packet = new WrapperPlayServerTeams(event);
            packet.getTeamInfo().ifPresent(info -> {
                if (info.getDisplayName() != null)
                    translateSimple(event, bread, info.getDisplayName(), info::setDisplayName);
                if (info.getPrefix() != null)
                    translateSimple(event, bread, info.getPrefix(), info::setPrefix);
                if (info.getSuffix() != null)
                    translateSimple(event, bread, info.getSuffix(), info::setSuffix);
            });
            return;
        }

        if (type == PacketType.Play.Server.OPEN_WINDOW) {
            var packet = new WrapperPlayServerOpenWindow(event);
            translateSimple(event, bread, packet.getTitle(), packet::setTitle);
            return;
        }

        if (type == PacketType.Play.Server.DISCONNECT) {
            var packet = new WrapperPlayServerDisconnect(event);
            translateSimple(event, bread, packet.getReason(), packet::setReason);
        }
    }

    private void translateSimple(PacketSendEvent event, BreadMaker bread, Component original, Consumer<Component> setter) {
        if (original == null) return;

        var locale = new Locale(original, bread.getLanguage())
            .setPlaceholders(bread)
            .setGendered(null)
            .setExecuteInteractive(bread);

        if (locale.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        Component translated;
        try {
            translated = locale.getColoredComponent(bread.getTheme());
        } catch (Exception e) {
            translated = null;
        }

        setter.accept(translated != null ? translated : original);
    }

    private void translateDelayable(PacketSendEvent event, Player player, BreadMaker bread, Component original,
                                     Consumer<Component> immediateSetter,
                                     Function<Component, WrapperPlayServerSystemChatMessage> delayedPacketFactory) {
        if (original == null) return;

        var locale = new Locale(original, bread.getLanguage())
            .setExecuteInteractive(bread)
            .setPlaceholders(bread)
            .setGendered(null);

        if (locale.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        if (locale.hasDelay()) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTaskLater(Spigot.getInstance(), () -> {
                Component translated;
                try {
                    translated = locale.getColoredComponent(bread.getTheme());
                } catch (Exception e) {
                    translated = null;
                }
                var delayed = delayedPacketFactory.apply(translated != null ? translated : original);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, delayed);
            }, locale.getDelay() * 20L);
            return;
        }

        Component translated;
        try {
            translated = locale.getColoredComponent(bread.getTheme());
        } catch (Exception e) {
            translated = null;
        }
        immediateSetter.accept(translated != null ? translated : original);
    }

    private void processScoreboardObjective(PacketSendEvent event) {
        var bread = Synergy.getBread(event.getUser().getUUID());
        var packet = new WrapperPlayServerScoreboardObjective(event);

        if (packet.getDisplayName() == null) return;

        var legacy = LegacyComponentSerializer.legacySection().serialize(packet.getDisplayName());
        var locale = Synergy.translate(legacy, bread.getLanguage())
            .setPlaceholders(bread).setGendered(null).setExecuteInteractive(bread);

        packet.setDisplayName(Component.text(locale.getColoredLegacy(bread.getTheme())));
    }

    private void processDeathScreen(PacketSendEvent event) {
        var bread = Synergy.getBread(event.getUser().getUUID());
        var packet = new WrapperPlayServerDeathCombatEvent(event);

        if (packet.getDeathMessage() == null) return;

        var locale = new Locale(packet.getDeathMessage(), bread.getLanguage())
            .setPlaceholders(bread)
            .setGendered(bread.getGender())
            .setExecuteInteractive(bread);

        packet.setDeathMessage(locale.getColoredComponent(bread.getTheme()));
    }

    private void processInventoryItems(PacketSendEvent event) {
        var bread = Synergy.getBread(event.getUser().getUUID());

        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            var packet = new WrapperPlayServerWindowItems(event);
            packet.setItems(packet.getItems().stream()
                .map(item -> SpigotConversionUtil.fromBukkitItemStack(
                    translateItem(SpigotConversionUtil.toBukkitItemStack(item), bread)))
                .toList());
        } else {
            var packet = new WrapperPlayServerSetSlot(event);
            packet.setItem(SpigotConversionUtil.fromBukkitItemStack(
                translateItem(SpigotConversionUtil.toBukkitItemStack(packet.getItem()), bread)));
        }
    }

    private void processEntityMetadata(PacketSendEvent event) {
        var bread = Synergy.getBread(event.getUser().getUUID());
        var packet = new WrapperPlayServerEntityMetadata(event);

        var dataValues = packet.getEntityMetadata();
        if (dataValues == null || dataValues.isEmpty()) return;

        List<EntityData<?>> updated = new ArrayList<>(dataValues);
        boolean modified = false;

        for (int i = 0; i < updated.size(); i++) {
            var dataValue = updated.get(i);
            if (dataValue == null || dataValue.getIndex() != 2) continue;

            try {
                var raw = dataValue.getValue();
                if (!(raw instanceof Optional<?> opt) || opt.isEmpty()) continue;

                var inner = opt.get();
                if (!(inner instanceof Component component)) continue;

                var locale = new Locale(component, bread.getLanguage())
                    .setPlaceholders(bread)
                    .setGendered(null)
                    .setExecuteInteractive(bread);

                var translated = locale.getColoredComponent(bread.getTheme());

                @SuppressWarnings({ "unchecked", "rawtypes" })
                var newData = new EntityData(dataValue.getIndex(), dataValue.getType(), Optional.of(translated));
                updated.set(i, newData);
                modified = true;

            } catch (Exception e) {
                Synergy.getLogger().error("Error translating hologram metadata: " + e.getMessage());
            }
        }

        if (modified) {
            packet.setEntityMetadata(updated);
        }
    }

    private void processMerchantOffers(PacketSendEvent event) {
        var bread = Synergy.getBread(event.getUser().getUUID());
        var packet = new WrapperPlayServerMerchantOffers(event);

        var offers = packet.getMerchantOffers();
        if (offers == null || offers.isEmpty()) return;

        for (var offer : offers) {
            offer.setFirstInputItem(SpigotConversionUtil.fromBukkitItemStack(
                translateItem(SpigotConversionUtil.toBukkitItemStack(offer.getFirstInputItem()), bread)));

            var second = offer.getSecondInputItem();
            if (second != null && !second.isEmpty()) {
                offer.setSecondInputItem(SpigotConversionUtil.fromBukkitItemStack(
                    translateItem(SpigotConversionUtil.toBukkitItemStack(second), bread)));
            }

            offer.setOutputItem(SpigotConversionUtil.fromBukkitItemStack(
                translateItem(SpigotConversionUtil.toBukkitItemStack(offer.getOutputItem()), bread)));
        }

        packet.setMerchantOffers(offers);
    }

    @SuppressWarnings("deprecation")
    private ItemStack translateItem(ItemStack item, BreadMaker bread) {
        if (item == null || !item.hasItemMeta()) return item;

        var meta = item.getItemMeta().clone();
        var visual = new ItemStack(item.getType(), item.getAmount());

        if (meta.hasDisplayName())
            meta.setDisplayName(Synergy.translate(meta.getDisplayName(), bread.getLanguage())
                .setPlaceholders(bread).setGendered(bread.getGender()).getColoredLegacy(bread.getTheme()));

        if (meta.hasLore())
            meta.setLore(meta.getLore().stream()
                .map(line -> Synergy.translate(line, bread.getLanguage())
                    .setPlaceholders(bread).setGendered(bread.getGender()).getColoredLegacy(bread.getTheme()))
                .toList());

        if (meta instanceof BookMeta book && item.getType() == Material.WRITTEN_BOOK) {
            var original = (BookMeta) item.getItemMeta();
            if (original.hasTitle())
                book.title(Synergy.translate(original.title(), bread.getLanguage())
                    .setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
            if (original.hasAuthor())
                book.author(Synergy.translate(original.author(), bread.getLanguage())
                    .setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
            if (original.hasPages())
                book.pages(original.getPages().stream()
                    .map(page -> Synergy.translate(page, bread.getLanguage())
                        .setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()))
                    .toList());
            meta = book;
        }

        visual.setItemMeta(meta);
        return visual;
    }

    @FunctionalInterface
    private interface PacketProcessor {
        void process(PacketSendEvent event);
    }
}