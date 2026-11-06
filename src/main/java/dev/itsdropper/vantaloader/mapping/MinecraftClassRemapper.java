package dev.itsdropper.vantaloader.mapping;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Remaps Mojang-named mod bytecode to the obfuscated names used by the vanilla client JAR. */
public final class MinecraftClassRemapper {
    private MinecraftClassRemapper() { }

    public static byte[] remap(byte[] classFile, MinecraftMappings mappings) {
        if (classFile == null) throw new NullPointerException("classFile");
        if (mappings == null) throw new NullPointerException("mappings");

        ClassReader reader = new ClassReader(classFile);
        ClassWriter writer = new ClassWriter(0);
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return mappings.toRuntimeClassName(internalName.replace('/', '.')).replace('.', '/');
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                return mappings.toRuntimeMethodName(owner.replace('/', '.'), name, descriptor);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                return mappings.toRuntimeFieldName(owner.replace('/', '.'), name, descriptor);
            }
        };
        reader.accept(new ClassRemapper(writer, remapper), 0);
        return writer.toByteArray();
    }
}
