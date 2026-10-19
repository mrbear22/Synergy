import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class Remap {

    static final String MAPPINGS = "synergy/mappings.tiny";

    public static void main(String[] args) throws Exception {
        if (args[0].equals("named")) {
            MemoryMappingTree tree = new MemoryMappingTree();
            MappingReader.read(Path.of(args[1]), MappingFormat.TINY_2_FILE, tree);
            MappingReader.read(Path.of(args[2]), MappingFormat.PROGUARD_FILE,
                new MappingNsRenamer(new MappingSourceNsSwitch(tree, "official"), Map.of("source", "named", "target", "official")));
            Path out = Path.of(args[4]);
            Files.deleteIfExists(out);
            try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(out).build()) {
                remap(tree, "official", Path.of(args[3]), consumer);
                for (int i = 5; i < args.length; i++) remap(tree, "intermediary", Path.of(args[i]), consumer);
            }
            try (FileSystem jar = FileSystems.newFileSystem(URI.create("jar:" + out.toUri()), Map.of())) {
                Path file = jar.getPath(MAPPINGS);
                Files.createDirectories(file.getParent());
                try (MappingWriter writer = MappingWriter.create(file, MappingFormat.TINY_2_FILE)) {
                    tree.accept(writer);
                }
            }
        } else {
            Path named = Path.of(args[1]);
            MemoryMappingTree tree = new MemoryMappingTree();
            try (FileSystem jar = FileSystems.newFileSystem(named)) {
                MappingReader.read(jar.getPath(MAPPINGS), MappingFormat.TINY_2_FILE, tree);
            }
            Path in = Path.of(args[2]), out = Path.of(args[3]);
            TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(provider(tree, "named", "intermediary")).build();
            try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(out).build()) {
                remapper.readClassPath(named);
                remapper.readInputs(in);
                remapper.apply(consumer);
                consumer.addNonClassFiles(in);
            } finally {
                remapper.finish();
            }
        }
    }

    static void remap(MappingTree tree, String from, Path input, OutputConsumerPath consumer) throws IOException {
        TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(provider(tree, from, "named")).ignoreConflicts(true).build();
        try {
            remapper.readInputs(input);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
    }

    static IMappingProvider provider(MappingTree tree, String from, String to) {
        int f = tree.getNamespaceId(from), t = tree.getNamespaceId(to);
        return acceptor -> tree.getClasses().forEach(c -> {
            String owner = c.getName(f);
            if (owner == null) return;
            if (c.getName(t) != null) acceptor.acceptClass(owner, c.getName(t));
            c.getMethods().forEach(m -> {
                if (m.getName(f) != null && m.getName(t) != null && m.getDesc(f) != null)
                    acceptor.acceptMethod(new IMappingProvider.Member(owner, m.getName(f), m.getDesc(f)), m.getName(t));
            });
            c.getFields().forEach(fd -> {
                if (fd.getName(f) != null && fd.getName(t) != null && fd.getDesc(f) != null)
                    acceptor.acceptField(new IMappingProvider.Member(owner, fd.getName(f), fd.getDesc(f)), fd.getName(t));
            });
        });
    }
}
