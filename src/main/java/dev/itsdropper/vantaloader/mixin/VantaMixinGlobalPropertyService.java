package dev.itsdropper.vantaloader.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Process-local Mixin blackboard, isolated from unrelated launchers. */
public final class VantaMixinGlobalPropertyService implements IGlobalPropertyService {
    private final ConcurrentMap<IPropertyKey, Object> properties = new ConcurrentHashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key) {
        return (T) properties.get(key);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null) properties.remove(key);
        else properties.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) properties.getOrDefault(key, defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = properties.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private record Key(String name) implements IPropertyKey {
        private Key {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Property key cannot be blank");
        }
    }
}
