package me.synergy.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.WritableBookMeta;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.AdventureComponentConverter;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class LocalesHandler {

    public void initialize() {
        if (!Config.getBoolean("localizations.enabled")) return;
        
        if (!Synergy.isDependencyAvailable("ProtocolLib")) {
            Synergy.getLogger().warning("ProtocolLib is required to initialize " + getClass().getSimpleName() + " module!");
            return;
        }

        try {
            registerPacketListeners();
            Synergy.getLogger().info(getClass().getSimpleName() + " module has been initialized!");
        } catch (Exception e) {
            Synergy.getLogger().error(getClass().getSimpleName() + " module failed to initialize:");
            e.printStackTrace();
        }
    }

    private void registerPacketListeners() {
        var manager = Synergy.getSpigot().getProtocolManager();
        
        manager.addPacketListener(createListener(ListenerPriority.HIGH, this::processComponents, 
            PacketType.Play.Server.SYSTEM_CHAT));
        
        manager.addPacketListener(createListener(ListenerPriority.MONITOR, this::processComponents,
            PacketType.Play.Server.SET_TITLE_TEXT, PacketType.Play.Server.SET_SUBTITLE_TEXT,
            PacketType.Play.Server.SET_ACTION_BAR_TEXT, PacketType.Play.Server.BOSS,
            PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER, PacketType.Play.Server.PLAYER_INFO,
            PacketType.Play.Server.SCOREBOARD_TEAM, PacketType.Play.Server.OPEN_WINDOW,
            PacketType.Play.Server.SPAWN_ENTITY, PacketType.Play.Server.KICK_DISCONNECT));
        
        manager.addPacketListener(createListener(ListenerPriority.MONITOR, this::processStringComponents,
            PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE, PacketType.Play.Server.SCOREBOARD_OBJECTIVE,
            PacketType.Play.Server.MAP));
        
        manager.addPacketListener(createListener(ListenerPriority.NORMAL, this::processInventoryItems,
            PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.SET_SLOT));
        
        manager.addPacketListener(createListener(ListenerPriority.NORMAL, this::processEntityMetadata,
        	    PacketType.Play.Server.ENTITY_METADATA));
    }

    private PacketAdapter createListener(ListenerPriority priority, PacketProcessor processor, PacketType... types) {
        return new PacketAdapter(Synergy.getSpigot(), priority, types) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    processor.process(event);
                } catch (Exception e) {
                    Synergy.getLogger().error("Error processing packet " + event.getPacketType() + ": " + e.getMessage());
                }
            }
        };
    }

    private void processComponents(PacketEvent event) {
        var packet = event.getPacket();
        var bread = Synergy.getBread(event.getPlayer().getUniqueId());
        
        for (int i = 0; i < packet.getChatComponents().size(); i++) {
            var wrapped = packet.getChatComponents().read(i);
            if (wrapped == null || wrapped.getJson() == null) continue;
            
            var locale = new Locale(AdventureComponentConverter.fromWrapper(wrapped), bread.getLanguage()).setExecuteInteractive(bread).setPlaceholders(bread).setGendered(null);
            
            if (locale.isCancelled()) {
                event.setCancelled(true);
                return;
            }
            
            if (locale.hasDelay()) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTaskLater(Synergy.getSpigot(), () -> {
                    try {
                        var delayed = new PacketContainer(event.getPacketType());
                        delayed.getChatComponents().write(0, AdventureComponentConverter.fromComponent(
                            locale.setExecuteInteractive(bread).getColoredComponent(bread.getTheme())));
                        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT) 
                            delayed.getBooleans().write(0, false);
                        ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), delayed);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, locale.getDelay() * 20L);
                return;
            }
            
            try {
                packet.getChatComponents().write(i, AdventureComponentConverter.fromComponent(
                    locale.setExecuteInteractive(bread).getColoredComponent(bread.getTheme())));
            } catch (Exception e) {
                packet.getChatComponents().write(i, AdventureComponentConverter.fromComponent(
                    locale.getColoredComponent(bread.getTheme())));
            }
        }
    }

    private void processStringComponents(PacketEvent event) {
        var packet = event.getPacket();
        var bread = Synergy.getBread(event.getPlayer().getUniqueId());
        
        packet.getStrings().getValues().stream()
            .filter(s -> s != null && !s.isEmpty())
            .forEach(original -> {
                int index = packet.getStrings().getValues().indexOf(original);
                packet.getStrings().write(index, Synergy.translate(original, bread.getLanguage())
                    .setPlaceholders(bread).setGendered(null).setExecuteInteractive(bread).getColoredLegacy(bread.getTheme()));
            });
    }

    private void processInventoryItems(PacketEvent event) {
        var packet = event.getPacket();
        var bread = Synergy.getBread(event.getPlayer().getUniqueId());
        
        if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
            var items = packet.getItemListModifier().read(0);
            if (items != null) {
                packet.getItemListModifier().write(0, 
                    items.stream().map(item -> translateItem(item, bread)).toList());
            }
        } else {
            var item = packet.getItemModifier().read(0);
            if (item != null) packet.getItemModifier().write(0, translateItem(item, bread));
        }
    }
    
    private void processEntityMetadata(PacketEvent event) {
        var packet = event.getPacket();
        var bread = Synergy.getBread(event.getPlayer().getUniqueId());

        var modifier = packet.getDataValueCollectionModifier();
        if (modifier == null || modifier.size() == 0) return;

        var dataValues = modifier.read(0);
        if (dataValues == null) return;

        boolean modified = false;

        for (var dataValue : dataValues) {
            if (dataValue == null || dataValue.getIndex() != 2) continue;

            try {
                var raw = dataValue.getValue();
                if (!(raw instanceof java.util.Optional<?> opt) || opt.isEmpty()) continue;

                var inner = opt.get();
                if (!(inner instanceof com.comphenix.protocol.wrappers.WrappedChatComponent wrapped)) continue;

                if (wrapped.getJson() == null) continue;

                var locale = new Locale(AdventureComponentConverter.fromWrapper(wrapped), bread.getLanguage())
                    .setPlaceholders(bread)
                    .setGendered(null)
                    .setExecuteInteractive(bread);

                var translated = AdventureComponentConverter.fromComponent(
                    locale.getColoredComponent(bread.getTheme()));

                dataValue.setValue(java.util.Optional.of(translated));
                modified = true;

            } catch (Exception e) {
                Synergy.getLogger().error("Error translating hologram metadata: " + e.getMessage());
            }
        }

        if (modified) {
            modifier.write(0, dataValues);
        }
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
            	original.getPages().forEach(p -> Synergy.getLogger().info("PAGE RAW: " + p));
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
        void process(PacketEvent event);
    }
}