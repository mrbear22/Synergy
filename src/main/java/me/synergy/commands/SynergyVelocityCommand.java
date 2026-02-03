package me.synergy.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;

import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.LocalesManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SynergyVelocityCommand {

    public void initialize() {
        LiteralCommandNode<CommandSource> synergyNode = LiteralArgumentBuilder
            .<CommandSource>literal("vsynergy")
            .requires(source -> source.hasPermission("synergy.admin"))
            .then(LiteralArgumentBuilder.<CommandSource>literal("reload")
                .executes(context -> {
                    CommandSource source = context.getSource();
                    
                    try {
                        new Config().initialize();
                        new DataManager().initialize();
                        new LocalesManager().initialize();
                        
                        source.sendMessage(Component.text("Synergy configuration reloaded!", NamedTextColor.GREEN));
                        Synergy.getLogger().info("Configuration reloaded by " + source);
                    } catch (Exception e) {
                        source.sendMessage(Component.text("Failed to reload: " + e.getMessage(), NamedTextColor.RED));
                        Synergy.getLogger().error("Failed to reload configuration" + e);
                    }
                    
                    return Command.SINGLE_SUCCESS;
                }))
            .executes(context -> {
                context.getSource().sendMessage(Component.text("Synergy Velocity Command. Usage: /vsynergy reload", NamedTextColor.YELLOW));
                return Command.SINGLE_SUCCESS;
            })
            .build();

        BrigadierCommand command = new BrigadierCommand(synergyNode);
        
        // Новий спосіб реєстрації для Velocity 3.0+
        CommandMeta meta = Velocity.getProxy().getCommandManager()
            .metaBuilder(command)
            .aliases("vsynergy")
            .plugin(Velocity.getInstance())
            .build();
        
        Velocity.getProxy().getCommandManager().register(meta, command);
        
        Synergy.getLogger().info("SynergyVelocityCommand has been registered!");
    }
}