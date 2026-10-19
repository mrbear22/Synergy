package me.synergy.objects;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import me.synergy.brains.Spigot;

public class ToastMessage {

	private final NamespacedKey key;
	private final String icon;
	private final String message;
	private final Style style;

	private ToastMessage(String icon, String message, Style style) {
		this.key = new NamespacedKey(Spigot.getInstance(), UUID.randomUUID().toString());
		this.icon = icon;
		this.message = message;
		this.style = style;
	}

	private void start(Player player) {
		createAdvancement();
		grantAdvancement(player);

		Bukkit.getScheduler().runTaskLater(Spigot.getInstance(), () -> {
			revokeAdvancement(player);
		}, 10);
	}

	@SuppressWarnings("deprecation")
	private void createAdvancement() {
		Bukkit.getUnsafe().loadAdvancement(key, "{\n" +
				"    \"criteria\": {\n" +
				"        \"trigger\": {\n" +
				"            \"trigger\": \"minecraft:impossible\"\n" +
				"        }\n" +
				"    },\n" +
				"    \"display\": {\n" +
				"        \"icon\": {\n" +
				"            \"item\": \"minecraft:" + icon + "\"\n" +
				"        },\n" +
				"        \"title\": {\n" +
				"            \"text\": \"" + message.replace("|", "\n") + "\"\n" +
				"        },\n" +
				"        \"description\": {\n" +
				"            \"text\": \"\"\n" +
				"        },\n" +
				"        \"background\": \"minecraft:textures/gui/advancements/backgrounds/adventure.png\",\n" +
				"        \"frame\": \"" + style.toString().toLowerCase() + "\",\n" +
				"        \"announce_to_chat\": false,\n" +
				"        \"show_toast\": true,\n" +
				"        \"hidden\": true\n" +
				"    },\n" +
				"    \"requirements\": [\n" +
				"        [\n" +
				"            \"trigger\"\n" +
				"        ]\n" +
				"    ]\n" +
				"}");
	}

	private void grantAdvancement(Player player) {
		player.getAdvancementProgress(Bukkit.getAdvancement(key)).awardCriteria("trigger");
	}

	private void revokeAdvancement(Player player) {
		player.getAdvancementProgress(Bukkit.getAdvancement(key)).revokeCriteria("trigger");
	}

	public static void displayTo(Player player, String icon, String message, Style style) {
		new ToastMessage(icon, message, style).start(player);
	}

	public static enum Style {
		GOAL,
		TASK,
		CHALLENGE
	}
	
}
