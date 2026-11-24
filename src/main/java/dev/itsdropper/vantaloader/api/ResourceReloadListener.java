package dev.itsdropper.vantaloader.api;

/** Creates a reversible replacement for resources changed on disk. */
@FunctionalInterface
public interface ResourceReloadListener {
    ResourceReloadPlan prepare(ResourceReloadContext context) throws Exception;
}
