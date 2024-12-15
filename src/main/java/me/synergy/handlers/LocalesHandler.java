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
				Synergy.getLogger().warning("ProtocolLib is required to initialize "+this.getClass().getSimpleName()+" module!");
				return;
			}

			Synergy.getSpigot().getProtocolManager().addPacketListener(
			    new PacketAdapter(Synergy.getSpigot(), ListenerPriority.HIGH, PacketType.Play.Server.SYSTEM_CHAT) {
			        @Override
			        public void onPacketSending(PacketEvent event) {
			            PacketContainer packet = event.getPacket();
			            BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
			            List<WrappedChatComponent> components = packet.getChatComponents().getValues();
			            
			            for (WrappedChatComponent component : components) {
			                String json = component.getJson();
			                try {
			                    component.setJson(Synergy.translate(json, bread.getLanguage())
				                    .setPlaceholders(bread)
			                        .setEndings(bread.getPronoun())
			                        .setExecuteInteractive(bread)
			                        .getColored(bread.getTheme()));
				                if (component.getJson().contains("<cancel_message>")) {
				                    event.setCancelled(true);
				                    return;
				                }
			                    packet.getChatComponents().write(components.indexOf(component), component);
			                } catch (Exception e) {
			                    component.setJson(Synergy.translate(json, bread.getLanguage())
					                    .setPlaceholders(bread)
				                        .setEndings(bread.getPronoun())
				                        .getLegacyColored(bread.getTheme()));
			                    packet.getChatComponents().write(components.indexOf(component), component);
			                    Synergy.getLogger().error("Error while processing chat message: " + e.getMessage());
			                }
			            }
			        }
			    }
			);

			Synergy.getSpigot().getProtocolManager().addPacketListener(
			    new PacketAdapter(Synergy.getSpigot(), ListenerPriority.MONITOR, 
			        PacketType.Play.Server.SET_TITLE_TEXT, 
			        PacketType.Play.Server.SET_SUBTITLE_TEXT) {
			        
			        @Override
			        public void onPacketSending(PacketEvent event) {
			            PacketContainer packet = event.getPacket();
			            BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
			            List<WrappedChatComponent> components = packet.getChatComponents().getValues();
			            
			            for (WrappedChatComponent component : components) {
			                try {
			                    component.setJson(Synergy.translate(component.getJson(), bread.getLanguage())
				                    .setPlaceholders(bread)
			                        .setEndings(bread.getPronoun())
			                        .setExecuteInteractive(bread)
			                        .getColored(bread.getTheme()));
			                    packet.getChatComponents().write(components.indexOf(component), component);
			                } catch (Exception e) {
			                    component.setJson(Synergy.translate(component.getJson(), bread.getLanguage())
			                        .getLegacyColored(bread.getTheme()));
			                    packet.getChatComponents().write(components.indexOf(component), component);
			                    Synergy.getLogger().error("Error while processing title/subtitle: " + e.getMessage());
			                }
			            }
			        }
			    }
			);

			Synergy.getSpigot().getProtocolManager().addPacketListener(
			    new PacketAdapter(Synergy.getSpigot(), ListenerPriority.NORMAL, PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.SET_SLOT) {
			        @Override
			        public void onPacketSending(PacketEvent event) {
		                Player player = event.getPlayer();
		                BreadMaker bread = Synergy.getBread(player.getUniqueId());
		                PacketContainer packet = event.getPacket();
			            try {
			                if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
			                    List<ItemStack> items = packet.getItemListModifier().read(0);
			                    for (int i = 0; i < items.size(); i++) {
			                        ItemStack item = items.get(i);
			                        if (item != null && item.hasItemMeta()) {
			                            item = item.clone();
			                            ItemMeta meta = item.getItemMeta();
			                            meta.setDisplayName(Synergy.translate(meta.getDisplayName(), bread.getLanguage())
			                            	.setPlaceholders(bread)
						                    .setEndings(bread.getPronoun())
			                            	.getLegacyColored(bread.getTheme()));

			                            List<String> lore = meta.getLore();
			                            if (lore != null) {
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
			                            
			                            item.setItemMeta(meta);
			                            items.set(i, item);
			                        }
			                    }
			                    packet.getItemListModifier().write(0, items);
			                } else if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
			                    ItemStack item = packet.getItemModifier().read(0);
			                    if (item != null && item.hasItemMeta()) {
			                        item = item.clone();
			                        ItemMeta meta = item.getItemMeta();
		                            meta.setDisplayName(Synergy.translate(meta.getDisplayName(), bread.getLanguage())
		                            	.setPlaceholders(bread)
					                    .setEndings(bread.getPronoun()).getLegacyColored(bread.getTheme()));
		                            List<String> lore = meta.getLore();
		                            if (lore != null) {
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
			                        item.setItemMeta(meta);
			                        packet.getItemModifier().write(0, item);
			                    }
			                }
			            } catch (Exception e) {
		                    Synergy.getLogger().error("Error while processing "+packet.getType()+": " + e.getMessage());
			            }
			        }
			    }
			);

			Synergy.getLogger().info(this.getClass().getSimpleName()+" module has been initialized!");
		} catch (Exception c) {
			Synergy.getLogger().error(this.getClass().getSimpleName()+" module failed to initialize:");
			c.printStackTrace();
		}
    }

}
