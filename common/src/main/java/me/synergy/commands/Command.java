package me.synergy.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import me.synergy.brains.Synergy;
import me.synergy.modules.Module;
import me.synergy.objects.LocaleBuilder;

public abstract class Command implements Module {

    private final List<String[]> syntax = new ArrayList<>();
    private final Map<String, String> labels = new LinkedHashMap<>();

    protected Command(String... lines) {
        for (String line : lines) {
            String[] tokens = line.split(" ");
            String[] aliases = tokens[0].split("\\|");
            for (String alias : aliases) labels.put(alias, aliases[0]);
            tokens[0] = aliases[0];
            syntax.add(tokens);
        }
    }

    protected abstract void execute(Sender sender, String path, String[] args);

    protected List<String> values(Sender sender, String name, String[] input) {
        return name.equals("player") ? Synergy.platform().playersByServer().values().stream().flatMap(List::stream).toList() : List.of();
    }

    protected String permission(String path) {
        return path.startsWith("synergy.") ? path : "synergy." + path;
    }

    protected boolean console(String path) {
        return false;
    }

    public final void run(Sender sender, String label, String[] args) {
        String[] input = input(label, Arrays.stream(args).filter(arg -> !arg.isEmpty()).toArray(String[]::new));
        String[] best = null;
        int literals = -1;
        for (String[] line : syntax) {
            int matched = match(line, input);
            if (matched > literals) {
                best = line;
                literals = matched;
            }
        }
        if (best == null) {
            help(sender);
            return;
        }
        String path = path(best);
        if (!allowed(sender, path)) sender.send("<locale:player.permission.missing>");
        else if (!sender.isPlayer() && !console(path)) sender.send("<locale:command.console.denied>");
        else execute(sender, path, values(best, input));
    }

    public final List<String> suggest(Sender sender, String label, String[] args) {
        String[] input = input(label, args);
        int last = input.length - 1;
        Set<String> result = new LinkedHashSet<>();
        for (String[] line : syntax) {
            if (!allowed(sender, path(line)) || !prefix(line, input, last)) continue;
            String token = line[Math.min(last, line.length - 1)];
            if (last >= line.length && !token.endsWith("...>") && !token.endsWith("...]")) continue;
            result.addAll(literal(token) ? List.of(token) : values(sender, name(token), input));
        }
        String typed = input[last].toLowerCase();
        return result.stream().filter(s -> s.toLowerCase().startsWith(typed)).toList();
    }

    protected void help(Sender sender) {
        List<String> lines = syntax.stream().filter(line -> allowed(sender, path(line)))
            .map(line -> LocaleBuilder.of("command.usage").placeholder("command", Synergy.platform().label(String.join(" ", line)).replace("<", "\\<"))
                .placeholder("description", "<locale:command." + path(line) + ">").build()).toList();
        sender.send(lines.isEmpty() ? "<locale:player.permission.missing>" : String.join("\n", lines));
    }

    public Set<String> labels() {
        return labels.keySet();
    }

    private boolean allowed(Sender sender, String path) {
        String node = permission(path);
        return node == null || sender.hasPermission(node);
    }

    private String[] input(String label, String[] args) {
        return Stream.concat(Stream.of(labels.getOrDefault(label.toLowerCase(), label)), Arrays.stream(args)).toArray(String[]::new);
    }

    private static int match(String[] line, String[] input) {
        int literals = 0;
        for (int i = 0; i < line.length; i++) {
            String token = line[i];
            if (token.endsWith("...>") || token.endsWith("...]")) return token.startsWith("[") || i < input.length ? literals : -1;
            if (i >= input.length) {
                if (token.startsWith("[")) continue;
                return -1;
            }
            if (literal(token)) {
                if (!token.equalsIgnoreCase(input[i])) return -1;
                literals++;
            }
        }
        return input.length <= line.length ? literals : -1;
    }

    private static boolean prefix(String[] line, String[] input, int last) {
        for (int i = 0; i < Math.min(last, line.length); i++) {
            if (literal(line[i]) && !line[i].equalsIgnoreCase(input[i])) return false;
            if (line[i].endsWith("...>") || line[i].endsWith("...]")) return true;
        }
        return true;
    }

    private static String[] values(String[] line, String[] input) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < line.length && i < input.length; i++) {
            if (literal(line[i])) continue;
            boolean rest = line[i].endsWith("...>") || line[i].endsWith("...]");
            values.add(rest ? String.join(" ", Arrays.copyOfRange(input, i, input.length)) : input[i]);
            if (rest) break;
        }
        return values.toArray(String[]::new);
    }

    private static String path(String[] line) {
        return String.join(".", Arrays.stream(line).filter(Command::literal).toList());
    }

    private static boolean literal(String token) {
        return !token.startsWith("<") && !token.startsWith("[");
    }

    private static String name(String token) {
        return token.replaceAll("[<>\\[\\].]", "");
    }

    @Override
    public Side side() {
        return Side.BACKEND;
    }

    @Override
    public String name() {
        return "/" + syntax.get(0)[0];
    }

    @Override
    public void start() {
        Synergy.platform().register(this);
    }

    @Override
    public void stop() {
        Synergy.platform().unregister(this);
    }
}
