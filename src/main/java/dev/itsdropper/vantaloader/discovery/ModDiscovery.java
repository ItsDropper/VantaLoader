package dev.itsdropper.vantaloader.discovery;

import dev.itsdropper.vantaloader.metadata.DescriptorReader;
import dev.itsdropper.vantaloader.metadata.ModDescriptor;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Discovers native VantaLoader mod JARs from a directory. */
public final class ModDiscovery {
    private final DescriptorReader reader = new DescriptorReader();

    public List<ModArtifact> discover(Path modsDirectory) throws IOException {
        Files.createDirectories(modsDirectory);
        if (!Files.isDirectory(modsDirectory)) throw new IOException("Mods path is not a directory: " + modsDirectory);
        List<Path> jars;
        try (var paths = Files.list(modsDirectory)) {
            jars = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        List<ModArtifact> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path jar : jars) {
            ModDescriptor descriptor = readDescriptor(jar);
            if (!ids.add(descriptor.id())) {
                throw new IOException("Duplicate mod id '" + descriptor.id() + "' found in " + jar.getFileName());
            }
            result.add(new ModArtifact(jar.toAbsolutePath().normalize(), descriptor));
        }
        return List.copyOf(result);
    }

    private ModDescriptor readDescriptor(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(DescriptorReader.FILE_NAME);
            if (entry == null) throw new IOException("Missing " + DescriptorReader.FILE_NAME);
            if (entry.getSize() > 256 * 1024) throw new IOException("Descriptor exceeds 256 KiB");
            try (var input = zip.getInputStream(entry);
                 var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return this.reader.read(new BoundedReader(reader, 256 * 1024));
            }
        } catch (IOException exception) {
            throw new IOException("Failed to inspect mod JAR '" + jar.getFileName()
                    + "': " + exception.getMessage(), exception);
        }
    }

    private static final class BoundedReader extends java.io.FilterReader {
        private int remaining;
        private BoundedReader(java.io.Reader reader, int limit) {
            super(reader);
            remaining = limit;
        }
        @Override public int read(char[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) {
                int extra = super.read();
                if (extra < 0) return -1;
                throw new IOException("Descriptor exceeds 256 KiB");
            }
            int count = super.read(buffer, offset, Math.min(length, remaining));
            if (count > 0) remaining -= count;
            return count;
        }
        @Override public int read() throws IOException {
            if (remaining == 0) {
                int extra = super.read();
                if (extra < 0) return -1;
                throw new IOException("Descriptor exceeds 256 KiB");
            }
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }
    }
}
