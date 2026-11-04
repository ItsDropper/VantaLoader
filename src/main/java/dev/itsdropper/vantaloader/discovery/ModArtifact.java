package dev.itsdropper.vantaloader.discovery;

import dev.itsdropper.vantaloader.metadata.ModDescriptor;
import java.nio.file.Path;

public record ModArtifact(Path path, ModDescriptor descriptor) { }
