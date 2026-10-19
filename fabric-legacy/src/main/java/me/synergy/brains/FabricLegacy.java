package me.synergy.brains;

import java.lang.reflect.Field;
import java.util.ArrayList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import me.synergy.commands.Sender;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.JsonOps;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.ChatHandler;
import me.synergy.modules.Messaging;
import me.synergy.modules.Tasks;
import me.synergy.handlers.PlayerSessions;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.title.Title;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class FabricLegacy implements DedicatedServerModInitializer, Platform, SynergyListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("Synergy");

    private static FabricLegacy INSTANCE;
    private MinecraftServer server;
    private final List<me.synergy.commands.Command> commands = new ArrayList<>();
    private GsonComponentSerializer json;

    public static final class Boot implements DedicatedServerModInitializer {
        @Override
        public void onInitializeServer() {
            boolean legacy = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("1.");
            try {
                ((DedicatedServerModInitializer) Class.forName(legacy ? "me.synergy.brains.FabricLegacy" : "me.synergy.brains.Fabric").getDeclaredConstructor().newInstance()).onInitializeServer();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("This Synergy build has no Fabric support for this Minecraft version", e);
            }
        }
    }

    @Override
    public void onInitializeServer() {
        INSTANCE = this;
        PayloadTypeRegistry.playC2S().register(Payload.TYPE, Payload.CODEC);
        PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> Messaging.receive(payload.data(), null));
        ServerLifecycleEvents.SERVER_STARTED.register(this::enable);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> disable());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s, resources, success) -> {
            commands.forEach(c -> addCommand(s.getCommands().getDispatcher(), c));
            refreshCommands();
        });
    }

    private void enable(MinecraftServer server) {
        this.server = server;
        json = GsonComponentSerializer.builder().options(JSONOptions.byDataVersion().at(SharedConstants.getCurrentVersion().getDataVersion().getVersion())).build();
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, p, params) -> {
            if (!ChatHandler.isEnabled()) return true;
            UUID uuid = p.getUUID();
            String name = p.getGameProfile().getName(), raw = message.signedContent();
            Tasks.async(() -> ChatHandler.handle(uuid, name, raw, new HashSet<>(online()), false));
            return false;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayer p = handler.getPlayer();
            PlayerSessions.joinAsync(p.getUUID(), p.getGameProfile().getName(), p.getIpAddress());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            ServerPlayer p = handler.getPlayer();
            PlayerSessions.quitAsync(p.getUUID(), p.getGameProfile().getName());
        });
        Synergy.getEventManager().registerEvents(this);
        Synergy.start(this);
        refreshCommands();
    }

    private void disable() {
        Synergy.stop();
    }

    @SynergyHandler("dispatch-command")
    public void onDispatch(SynergyEvent event) {
        console(event.getOption("command").getAsString());
    }

    public static FabricLegacy getInstance() {
        return INSTANCE;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Optional<ServerPlayer> player(UUID uuid) {
        return Optional.ofNullable(uuid == null ? null : server.getPlayerList().getPlayer(uuid));
    }

    public net.minecraft.network.chat.Component toVanilla(Component component) {
        return ComponentSerialization.CODEC
            .parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json.serializeToTree(component))
            .getOrThrow();
    }

    @Override
    public void register(me.synergy.commands.Command command) {
        commands.add(command);
        addCommand(server.getCommands().getDispatcher(), command);
        refreshCommands();
    }

    @Override
    public void unregister(me.synergy.commands.Command command) {
        commands.remove(command);
        command.labels().forEach(label -> removeNode(server.getCommands().getDispatcher().getRoot(), label));
        refreshCommands();
    }

    @Override
    public void dispatch(UUID uuid, String command) {
        server.execute(() -> player(uuid).map(ServerPlayer::createCommandSourceStack).or(() -> uuid == null ? Optional.of(server.createCommandSourceStack()) : Optional.empty())
            .ifPresent(source -> server.getCommands().performPrefixedCommand(source, command)));
    }

    @Override
    public List<String> complete(UUID uuid, String input) {
        var source = player(uuid).map(ServerPlayer::createCommandSourceStack).orElse(server.createCommandSourceStack());
        var dispatcher = server.getCommands().getDispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join().getList().stream().map(Suggestion::getText).toList();
    }

    private void addCommand(CommandDispatcher<CommandSourceStack> dispatcher, me.synergy.commands.Command command) {
        for (String label : command.labels()) {
            dispatcher.register(Commands.literal(label)
                .executes(ctx -> runCommand(command, label, ctx.getSource(), ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> suggest(command, label, ctx.getSource(), builder))
                    .executes(ctx -> runCommand(command, label, ctx.getSource(), StringArgumentType.getString(ctx, "args")))));
        }
    }

    private int runCommand(me.synergy.commands.Command command, String label, CommandSourceStack source, String input) {
        Sender sender = sender(source);
        command.run(sender, label, input.split(" "));
        return 1;
    }

    private CompletableFuture<Suggestions> suggest(me.synergy.commands.Command command, String label, CommandSourceStack source, SuggestionsBuilder builder) {
        String input = builder.getRemaining();
        String[] args = input.split(" ", -1);
        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + input.lastIndexOf(' ') + 1);
        command.suggest(sender(source), label, args).forEach(offset::suggest);
        return offset.buildFuture();
    }

    private Sender sender(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? Sender.CONSOLE : new Sender(player.getUUID(), player.getGameProfile().getName());
    }

    private void refreshCommands() {
        guardCommands(server.getCommands().getDispatcher().getRoot());
        server.getPlayerList().getPlayers().forEach(p -> server.getCommands().sendCommands(p));
    }

    @SuppressWarnings("unchecked")
    private static void guardCommands(CommandNode<CommandSourceStack> node) {
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            com.mojang.brigadier.Command<CommandSourceStack> original = child.getCommand();
            if (original != null && !(original instanceof CommandGuard)) {
                try {
                    Field field = CommandNode.class.getDeclaredField("command");
                    field.setAccessible(true);
                    field.set(child, new CommandGuard(original));
                } catch (ReflectiveOperationException e) {
                    return;
                }
            }
            guardCommands(child);
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeNode(CommandNode<CommandSourceStack> root, String label) {
        for (String name : new String[] {"children", "literals"}) {
            try {
                Field field = CommandNode.class.getDeclaredField(name);
                field.setAccessible(true);
                ((Map<String, ?>) field.get(root)).remove(label);
            } catch (ReflectiveOperationException e) {
                return;
            }
        }
    }

    private record CommandGuard(com.mojang.brigadier.Command<CommandSourceStack> original) implements com.mojang.brigadier.Command<CommandSourceStack> {
        @Override
        public int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            ServerPlayer p = ctx.getSource().getPlayer();
            return p != null && ChatHandler.blockCommand(p.getUUID(), p.getGameProfile().getName(), "/" + ctx.getInput()) ? 0 : original.run(ctx);
        }
    }

    @Override public String name() { return "fabric"; }
    @Override public boolean isProxy() { return false; }

    @Override
    public String version() {
        return FabricLoader.getInstance().getModContainer("synergy")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    @Override public void info(String message) { LOGGER.info(message); }
    @Override public void warning(String message) { LOGGER.warn(message); }
    @Override public void error(String message) { LOGGER.error(message); }


    @Override
    public void console(String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    @Override public boolean hasDependency(String id) { return FabricLoader.getInstance().isModLoaded(id.toLowerCase()); }

    @Override
    public boolean send(String server, byte[] data) {
        var player = this.server.getPlayerList().getPlayers().stream().findFirst();
        player.ifPresent(p -> ServerPlayNetworking.send(p, new Payload(data)));
        return player.isPresent();
    }

    @Override public Collection<UUID> online() { return server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList(); }
    @Override public boolean isOnline(UUID uuid) { return player(uuid).isPresent(); }

    @Override
    public boolean hasPermission(UUID uuid, String node) {
        return player(uuid).map(p -> hasDependency("fabric-permissions-api-v0")
            ? PermissionsApi.check(p, node)
            : p.hasPermissions(2)).orElse(false);
    }

    @Override
    public String locale(UUID uuid) {
        return player(uuid).map(p -> p.clientInformation().language().split("_")[0]).orElse(null);
    }

    @Override public Audience audience(UUID uuid) { return player(uuid).<Audience>map(PlayerAudience::new).orElse(Audience.empty()); }
    @Override public void kick(UUID uuid, Component reason) { player(uuid).ifPresent(p -> server.execute(() -> p.connection.disconnect(toVanilla(reason)))); }

    @Override
    public Map<String, List<String>> playersByServer() {
        return Map.of(Synergy.getServerName(), server.getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()).toList());
    }

    @Override
    public Optional<Position> position(UUID uuid) {
        return player(uuid).map(p -> new Position(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ()));
    }

    private static final class PermissionsApi {
        static boolean check(ServerPlayer player, String node) {
            return me.lucko.fabric.api.permissions.v0.Permissions.check(player, node, 2);
        }
    }

    private record PlayerAudience(ServerPlayer player) implements Audience {
        @Override
        public void openBook(Book book) {
            ItemStack item = new ItemStack(Items.WRITTEN_BOOK);
            item.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(PlainTextComponentSerializer.plainText().serialize(book.title())),
                PlainTextComponentSerializer.plainText().serialize(book.author()), 0, book.pages().stream().map(page -> Filterable.passThrough(INSTANCE.toVanilla(page))).toList(), true));
            int slot = 36 + player.getInventory().selected;
            player.connection.send(new ClientboundContainerSetSlotPacket(0, player.inventoryMenu.incrementStateId(), slot, item));
            player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
            player.connection.send(new ClientboundContainerSetSlotPacket(0, player.inventoryMenu.incrementStateId(), slot, player.getMainHandItem()));
        }

        @Override
        public void sendMessage(Component message) {
            player.sendSystemMessage(INSTANCE.toVanilla(message));
        }

        @Override
        public void sendActionBar(Component message) {
            player.sendSystemMessage(INSTANCE.toVanilla(message), true);
        }

        @Override
        public void showTitle(Title title) {
            Title.Times t = title.times();
            if (t != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(ticks(t.fadeIn()), ticks(t.stay()), ticks(t.fadeOut())));
            }
            player.connection.send(new ClientboundSetSubtitleTextPacket(INSTANCE.toVanilla(title.subtitle())));
            player.connection.send(new ClientboundSetTitleTextPacket(INSTANCE.toVanilla(title.title())));
        }

        @Override
        public void playSound(Sound sound) {
            Key key = sound.name();
            Holder<SoundEvent> event = Holder.direct(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(key.namespace(), key.value())));
            player.connection.send(new ClientboundSoundPacket(event, SoundSource.AMBIENT, player.getX(), player.getY(), player.getZ(),
                sound.volume(), sound.pitch(), player.getRandom().nextLong()));
        }

        private static int ticks(java.time.Duration duration) {
            return (int) (duration.toMillis() / 50);
        }
    }

    public record Payload(byte[] data) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("net", "synergy"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data()),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new Payload(bytes);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
