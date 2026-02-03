package me.synergy.commands;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import me.synergy.brains.Spigot;
import me.synergy.brains.Synergy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class DynamicCommands implements CommandExecutor {
    private static Set<String> commands;
    private static CommandMap commandMap;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    
    public void initialize() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to get CommandMap: " + e.getMessage());
            return;
        }
        
        commands = Synergy.getConfig().getConfigurationSection("commands").keySet();
        for (String cmd : commands) {
            registerCommand(cmd);
        }
        Synergy.getLogger().info(getClass().getSimpleName() + " initialized with " + commands.size() + " commands");
    }
    
    private void registerCommand(String name) {
        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            c.setAccessible(true);
            PluginCommand cmd = c.newInstance(name, Spigot.getInstance());
            cmd.setExecutor(this);
            commandMap.register(Spigot.getInstance().getPluginMeta().getName(), cmd);
        } catch (Exception e) {
            Synergy.getLogger().warning("Failed to register /" + name);
        }
    }
    
    public void shutdown() {
        for (String cmdName : commands) {
            Command cmd = commandMap.getCommand(cmdName);
            if (cmd != null) {
                cmd.unregister(commandMap);
            }
        }
        commands = null;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!commands.contains(command.getName())) return false;
        
        Object messageObj = Synergy.getConfig().get("commands." + command.getName() + ".message");
        if (messageObj == null) return false;
        
        if (messageObj instanceof java.util.List) {
            for (String line : (java.util.List<String>) messageObj) {
                Component component = LEGACY_SERIALIZER.deserialize(line);
                sender.sendMessage(component);
            }
        } else {
            for (String line : messageObj.toString().split("\\n")) {
                if (!line.trim().isEmpty()) {
                    Component component = LEGACY_SERIALIZER.deserialize(line);
                    sender.sendMessage(component);
                }
            }
        }
        return true;
    }
}