package dev.itsdropper.vantaloader.api;
import java.io.IOException;
import java.util.Set;
/** Persistent, type-safe configuration owned by one mod. */
public interface ModConfig {
 <T>T get(ConfigKey<T> key);
 <T>void set(ConfigKey<T> key,T value);
 boolean contains(String key);
 void remove(String key);
 Set<String> keys();
 void save() throws IOException;
 /** Replaces in-memory state from disk; unsaved changes are discarded. */
 void reload() throws IOException;
}
