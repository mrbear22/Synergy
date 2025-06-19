package me.synergy.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

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
import me.synergy.utils.Timings;

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

			addChatPacketListener();
			
			addChatComponentListener("Title/Subtitle", ListenerPriority.MONITOR, 
				PacketType.Play.Server.SET_TITLE_TEXT, 
				PacketType.Play.Server.SET_SUBTITLE_TEXT);
				
			addChatComponentListener("ActionBar", ListenerPriority.MONITOR, 
				PacketType.Play.Server.SET_ACTION_BAR_TEXT);
				
			addChatComponentListener("BossBar", ListenerPriority.MONITOR, 
				PacketType.Play.Server.BOSS);
				
			addChatComponentListener("TAB Header/Footer", ListenerPriority.MONITOR, 
				PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER);
				
			addChatComponentListener("Player Info", ListenerPriority.MONITOR, 
				PacketType.Play.Server.PLAYER_INFO);
				
			addChatComponentListener("Scoreboard Objective", ListenerPriority.MONITOR, 
				PacketType.Play.Server.SCOREBOARD_OBJECTIVE);

			addStringPacketListener("Scoreboard Display", ListenerPriority.MONITOR, 
				PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE);
				
			addStringPacketListener("Scoreboard Score", ListenerPriority.MONITOR, 
				PacketType.Play.Server.SCOREBOARD_SCORE);

			addInventoryPacketListener();

			Synergy.getLogger().info(this.getClass().getSimpleName()+" module has been initialized!");
		} catch (Exception c) {
			Synergy.getLogger().error(this.getClass().getSimpleName()+" module failed to initialize:");
			c.printStackTrace();
		}
	}
	
	private void addChatPacketListener() {
		Synergy.getSpigot().getProtocolManager().addPacketListener(
			new PacketAdapter(Synergy.getSpigot(), ListenerPriority.HIGH, PacketType.Play.Server.SYSTEM_CHAT) {
				@Override
				public void onPacketSending(PacketEvent event) {
					Timings timing = new Timings();
					timing.startTiming("Chat");
					
					processChatComponents(event.getPacket(), event.getPlayer(), (component, translatedJson) -> {
						component.setJson(translatedJson);
						if (translatedJson.contains("<cancel_message>")) {
							event.setCancelled(true);
						}
					});
					
					timing.endTiming("Chat");
				}
			}
		);
	}

	private void addChatComponentListener(String name, ListenerPriority priority, PacketType... packetTypes) {
		Synergy.getSpigot().getProtocolManager().addPacketListener(
			new PacketAdapter(Synergy.getSpigot(), priority, packetTypes) {
				@Override
				public void onPacketSending(PacketEvent event) {
					processChatComponents(event.getPacket(), event.getPlayer(), 
						(component, translatedJson) -> component.setJson(translatedJson), name);
				}
			}
		);
	}

	private void addStringPacketListener(String name, ListenerPriority priority, PacketType... packetTypes) {
		Synergy.getSpigot().getProtocolManager().addPacketListener(
			new PacketAdapter(Synergy.getSpigot(), priority, packetTypes) {
				@Override
				public void onPacketSending(PacketEvent event) {
					processStringValues(event.getPacket(), event.getPlayer(), name);
				}
			}
		);
	}

	private void addInventoryPacketListener() {
		Synergy.getSpigot().getProtocolManager().addPacketListener(
			new PacketAdapter(Synergy.getSpigot(), ListenerPriority.NORMAL, 
				PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.SET_SLOT) {
				@Override
				public void onPacketSending(PacketEvent event) {
					Player player = event.getPlayer();
					BreadMaker bread = Synergy.getBread(player.getUniqueId());
					PacketContainer packet = event.getPacket();
					
					try {
						if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
							processItemList(packet, bread);
						} else if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
							processSingleItem(packet, bread);
						}
					} catch (Exception e) {
						Synergy.getLogger().error("Error while processing "+packet.getType()+": " + e.getMessage());
					}
				}
			}
		);
	}

	private void processChatComponents(PacketContainer packet, Player player, 
			BiConsumer<WrappedChatComponent, String> componentProcessor) {
		processChatComponents(packet, player, componentProcessor, "ChatComponent");
	}

	private void processChatComponents(PacketContainer packet, Player player, 
			BiConsumer<WrappedChatComponent, String> componentProcessor, String logContext) {
		BreadMaker bread = Synergy.getBread(player.getUniqueId());
		List<WrappedChatComponent> components = packet.getChatComponents().getValues();
		
		for (WrappedChatComponent component : components) {
			if (component != null && component.getJson() != null) {
				String originalJson = component.getJson();
				try {
					String translatedJson = translateText(originalJson, bread, true);
					componentProcessor.accept(component, translatedJson);
					packet.getChatComponents().write(components.indexOf(component), component);
				} catch (Exception e) {
					try {
						String fallbackJson = translateText(originalJson, bread, false);
						componentProcessor.accept(component, fallbackJson);
						packet.getChatComponents().write(components.indexOf(component), component);
					} catch (Exception fallbackError) {
						Synergy.getLogger().error("Error while processing " + logContext + " (even fallback failed): " + fallbackError.getMessage());
					}
				}
			}
		}
	}

	private void processStringValues(PacketContainer packet, Player player, String logContext) {
		BreadMaker bread = Synergy.getBread(player.getUniqueId());
		
		try {
			List<String> strings = packet.getStrings().getValues();
			for (int i = 0; i < strings.size(); i++) {
				String originalString = strings.get(i);
				if (originalString != null && shouldTranslateString(i, logContext)) {
					String translatedString = translateText(originalString, bread, false);
					packet.getStrings().write(i, translatedString);
				}
			}
		} catch (Exception e) {
			Synergy.getLogger().error("Error while processing " + logContext + ": " + e.getMessage());
		}
	}

	private boolean shouldTranslateString(int index, String context) {
		if (context.contains("Scoreboard Objective") && index == 0) {
			return false;
		}
		return true;
	}

	private void processItemList(PacketContainer packet, BreadMaker bread) {
		List<ItemStack> items = packet.getItemListModifier().read(0);
		for (int i = 0; i < items.size(); i++) {
			ItemStack item = items.get(i);
			if (item != null && item.hasItemMeta()) {
				ItemStack translatedItem = translateItem(item, bread);
				items.set(i, translatedItem);
			}
		}
		packet.getItemListModifier().write(0, items);
	}

	private void processSingleItem(PacketContainer packet, BreadMaker bread) {
		ItemStack item = packet.getItemModifier().read(0);
		if (item != null && item.hasItemMeta()) {
			ItemStack translatedItem = translateItem(item, bread);
			packet.getItemModifier().write(0, translatedItem);
		}
	}

	private ItemStack translateItem(ItemStack item, BreadMaker bread) {
		item = item.clone();
		ItemMeta meta = item.getItemMeta();
		
		// Переклад назви
		if (meta.getDisplayName() != null) {
			meta.setDisplayName(translateText(meta.getDisplayName(), bread, false));
		}
		
		List<String> lore = meta.getLore();
		if (lore != null) {
			List<String> translatedLore = new ArrayList<>();
			for (String line : lore) {
				translatedLore.add(translateText(line, bread, false));
			}
			meta.setLore(translatedLore);
		}
		
		item.setItemMeta(meta);
		return item;
	}

	private String translateText(String text, BreadMaker bread, boolean useColored) {
		if (text == null) return null;
		
		var translator = Synergy.translate(text, bread.getLanguage())
			.setPlaceholders(bread)
			.setEndings(bread.getPronoun());
		
		if (useColored) {
			return translator.setExecuteInteractive(bread).getColored(bread.getTheme());
		} else {
			return translator.getLegacyColored(bread.getTheme());
		}
	}
}