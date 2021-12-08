package nl.thijsalders.spigotproxy;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.bukkit.Bukkit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Mapping {
    private static final String MOJANG_NAMESPACE = "mojang+yarn";
    private static final String SPIGOT_NAMESPACE = "spigot";

    private final MappingTree tree = new MemoryMappingTree();
    private final int mojangNamespaceId;
    private final int spigotNamespaceId;

    Mapping() throws IOException {
        try (final InputStream stream = Bukkit.getServer().getClass().getClassLoader().getResourceAsStream("META-INF/mappings/reobf.tiny")) {
            if (stream == null) {
                throw new FileNotFoundException();
            }

            MappingReader.read(new InputStreamReader(stream), MappingFormat.TINY_2, (MappingVisitor) tree);
        }

        this.mojangNamespaceId = tree.getNamespaceId(MOJANG_NAMESPACE);
        this.spigotNamespaceId = tree.getNamespaceId(SPIGOT_NAMESPACE);
    }

    public String mapClassName(final String mojangClassName) {
        return tree.getClass(mojangClassName, this.mojangNamespaceId)
                .getName(this.spigotNamespaceId);
    }

    public String mapFieldName(final String mojangClassName, final String mojangName, final String desc) {
        return tree.getField(mojangClassName, mojangName, desc, this.mojangNamespaceId)
                .getName(this.spigotNamespaceId);
    }
}
