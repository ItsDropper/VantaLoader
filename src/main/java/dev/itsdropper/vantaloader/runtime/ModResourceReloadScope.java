package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ResourceReloadListener;
import dev.itsdropper.vantaloader.api.ResourceReloadPlan;
import dev.itsdropper.vantaloader.api.ResourceReloadRegistry;
import dev.itsdropper.vantaloader.api.ResourceReloadSubscription;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Owns registrations for one mod and restores its class-loader context during callbacks. */
final class ModResourceReloadScope implements ResourceReloadRegistry, AutoCloseable {
    private final String modId;
    private final ClassLoader classLoader;
    private final ResourceReloadService service;
    private final List<ResourceReloadSubscription> owned = new ArrayList<>();
    private boolean closed;

    ModResourceReloadScope(String modId, ClassLoader classLoader, ResourceReloadService service) {
        this.modId = modId;
        this.classLoader = classLoader;
        this.service = service;
    }

    @Override public synchronized ResourceReloadSubscription register(
            String id, Path directory, ResourceReloadListener listener) {
        if (closed) throw new IllegalStateException("Resource reload scope is closed");
        ResourceReloadSubscription subscription = service.register(modId, id, directory, context ->
                withContext(() -> {
                    ResourceReloadPlan delegate = listener.prepare(context);
                    return new ResourceReloadPlan() {
                        @Override public void commit() throws Exception {
                            withContext(() -> { delegate.commit(); return null; });
                        }
                        @Override public void rollback() throws Exception {
                            withContext(() -> { delegate.rollback(); return null; });
                        }
                    };
                }));
        owned.add(subscription);
        return () -> {
            synchronized (ModResourceReloadScope.this) {
                subscription.close();
                owned.remove(subscription);
            }
        };
    }

    private <T> T withContext(java.util.concurrent.Callable<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try { return action.call(); }
        finally { thread.setContextClassLoader(previous); }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        owned.forEach(ResourceReloadSubscription::close);
        owned.clear();
    }
}
