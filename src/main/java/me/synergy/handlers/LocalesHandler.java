package me.synergy.handlers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;

public class LocalesHandler {

    public void initialize() {
        try {
            if (!Synergy.getConfig().getBoolean("localizations.enabled")) {
                return;
            }
            if (!Synergy.isDependencyAvailable("ProtocolLib")) {
                Synergy.getLogger().warning("ProtocolLib is required to initialize " + this.getClass().getSimpleName() + " module!");
                return;
            }

            registerChatPackets();
            registerDisplayPackets();
            registerScoreboardPackets();
            registerInventoryPackets();

            Synergy.getLogger().info(this.getClass().getSimpleName() + " module has been initialized!");
        } catch (Exception e) {
            Synergy.getLogger().error(this.getClass().getSimpleName() + " module failed to initialize:");
            e.printStackTrace();
        }
    }

    private void registerChatPackets() {
        // Chat messages
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.HIGH, PacketType.Play.Server.SYSTEM_CHAT) {
                @Override
                public void onPacketSending(PacketEvent event) {                    
                    try {
                        processChatComponents(event);
                    } catch (Exception e) {
                        Synergy.getLogger().error("Error processing chat: " + e.getMessage());
                    }
                }
            }
        );
    }

    private void registerDisplayPackets() {
        // Title, Subtitle, ActionBar
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, 
                PacketType.Play.Server.SET_TITLE_TEXT, 
                PacketType.Play.Server.SET_SUBTITLE_TEXT,
                PacketType.Play.Server.SET_ACTION_BAR_TEXT) {
                
                @Override
                public void onPacketSending(PacketEvent event) {
                    processChatComponents(event);
                }
            }
        );

        // BossBar
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, PacketType.Play.Server.BOSS) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    processChatComponents(event);
                }
            }
        );

        // TAB Header/Footer
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, 
                PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER) {
                
                @Override
                public void onPacketSending(PacketEvent event) {
                    processChatComponents(event);
                }
            }
        );

        // Player Info (TAB names)
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, PacketType.Play.Server.PLAYER_INFO) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    processChatComponents(event);
                }
            }
        );
    }

    private void registerScoreboardPackets() {
        // Scoreboard Display
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, 
                PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE) {
                
                @Override
                public void onPacketSending(PacketEvent event) {
                    processStringComponents(event);
                }
            }
        );

        // Scoreboard Objective
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, 
                PacketType.Play.Server.SCOREBOARD_OBJECTIVE) {
                
                @Override
                public void onPacketSending(PacketEvent event) {
                //    processChatComponents(event);
                    processStringComponents(event);
                }
            }
        );

        // Scoreboard Score
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, 
                PacketType.Play.Server.SCOREBOARD_SCORE) {
                
                @Override
                public void onPacketSending(PacketEvent event) {
                //    processStringComponents(event);
                }
            }
        );
    }

    private void registerInventoryPackets() {
        Synergy.getSpigot().getProtocolManager().addPacketListener(
            new PacketAdapter(Synergy.getSpigot(), ListenerPriority.NORMAL, 
                PacketType.Play.Server.WINDOW_ITEMS, 
                PacketType.Play.Server.SET_SLOT) {
                
                @Override
                public void onPacketSending(PacketEvent event) {
                    processInventoryItems(event);
                }
            }
        );
    }

    private void processChatComponents(PacketEvent event) {
        try {
            PacketContainer packet = event.getPacket();
            BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
            List<WrappedChatComponent> components = packet.getChatComponents().getValues();
            
            for (int i = 0; i < components.size(); i++) {
                WrappedChatComponent component = components.get(i);
                if (component != null && component.getJson() != null) {
                    String json = component.getJson();
                    
                    //new Logger().info("before: " +json);
                    
                    if (json.contains("<cancel_message>")) {
                        event.setCancelled(true);
                        return;
                    }
                    
                    String translatedJson = processJsonComponent(json, bread);
                    
                    if (translatedJson.contains("<cancel_message>")) {
                        event.setCancelled(true);
                        return;
                    }
                    
                    //new Logger().info("after: " +translatedJson);
                    
                    component.setJson(translatedJson);
                    packet.getChatComponents().write(i, component);
                }
            }
        } catch (Exception e) {
        	e.printStackTrace();
            Synergy.getLogger().error("Error processing chat components in " + event.getPacketType() + ": " + e.getMessage());
        }
    }

    private void processStringComponents(PacketEvent event) {
        try {
            PacketContainer packet = event.getPacket();
            BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
            List<String> strings = packet.getStrings().getValues();
            
            for (int i = 0; i < strings.size(); i++) {
                String original = strings.get(i);
                if (original != null && !original.isEmpty()) {
                    String translated = Synergy.translate(original, bread.getLanguage())
                        .setPlaceholders(bread)
                        .setEndings(null)
                        .getLegacyColored(bread.getTheme());
                    packet.getStrings().write(i, translated);
                }
            }
        } catch (Exception e) {
            Synergy.getLogger().error("Error processing string components in " + event.getPacketType() + ": " + e.getMessage());
        }
    }

    private void processInventoryItems(PacketEvent event) {
        try {
            Player player = event.getPlayer();
            BreadMaker bread = Synergy.getBread(player.getUniqueId());
            PacketContainer packet = event.getPacket();
            
            if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
                List<ItemStack> items = packet.getItemListModifier().read(0);
                List<ItemStack> translatedItems = new ArrayList<>();
                
                for (ItemStack item : items) {
                    translatedItems.add(translateItemVisual(item, bread));
                }
                
                packet.getItemListModifier().write(0, translatedItems);
            } else if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
                ItemStack item = packet.getItemModifier().read(0);
                ItemStack translatedItem = translateItemVisual(item, bread);
                packet.getItemModifier().write(0, translatedItem);
            }
        } catch (Exception e) {
            Synergy.getLogger().error("Error processing inventory items: " + e.getMessage());
        }
    }

    private String processJsonComponent(String json, BreadMaker bread) {
        try {

            return Synergy.translate(json, bread.getLanguage())
                    .setPlaceholders(bread)
                    .setEndings(null)
                    .setExecuteInteractive(bread)
                    .getColored(bread.getTheme());
        } catch (Exception e) {
            return Synergy.translate(json, bread.getLanguage())
                    .setPlaceholders(bread)
                    .setEndings(bread.getPronoun())
                    .getColored(bread.getTheme());
        }
    }

    private ItemStack translateItemVisual(ItemStack item, BreadMaker bread) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }

        ItemStack visualItem = item.clone();
        ItemMeta meta = visualItem.getItemMeta();
        
        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            String translatedName = Synergy.translate(displayName, bread.getLanguage())
                .setPlaceholders(bread)
                .setEndings(bread.getPronoun())
                .getLegacyColored(bread.getTheme());
            meta.setDisplayName(translatedName);
        }

        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            List<String> translatedLore = new ArrayList<>();
            
            for (String line : lore) {
                String translatedLine = Synergy.translate(line, bread.getLanguage())
                    .setPlaceholders(bread)
                    .setEndings(bread.getPronoun())
                    .getLegacyColored(bread.getTheme());
                translatedLore.add(translatedLine);
            }
            
            meta.setLore(translatedLore);
        }
        
        visualItem.setItemMeta(meta);
        return visualItem;
    }
}