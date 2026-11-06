package dev.itsdropper.vantaloader.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.MixinServiceAbstract;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Mixin host service for a plain Instrumentation-based Minecraft launch. */
public final class VantaMixinService extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider, IClassTracker, ITransformerProvider {
    private static volatile IMixinTransformer transformer;

    @Override public String getName() { return "VantaLoader Java Agent"; }
    @Override public boolean isValid() { return true; }
    @Override public Collection<String> getPlatformAgents() {
        return List.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }
    @Override public IContainerHandle getPrimaryContainer() {
        try {
            return new ContainerHandleURI(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot resolve VantaLoader's Mixin container", exception);
        }
    }
    @Override public Collection<IContainerHandle> getMixinContainers() {
        List<IContainerHandle> result = new ArrayList<>();
        for (URL url : MixinClassPath.containers()) {
            try { result.add(new ContainerHandleURI(url.toURI())); }
            catch (Exception exception) {
                throw new IllegalStateException("Invalid Mixin mod container URL: " + url, exception);
            }
        }
        return List.copyOf(result);
    }
    @Override public InputStream getResourceAsStream(String name) {
        for (ClassLoader loader : MixinClassPath.loaders()) {
            InputStream stream = loader.getResourceAsStream(name);
            if (stream != null) return stream;
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            InputStream stream = context.getResourceAsStream(name);
            if (stream != null) return stream;
        }
        return VantaMixinService.class.getClassLoader().getResourceAsStream(name);
    }
    @Override public IClassProvider getClassProvider() { return this; }
    @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
    @Override public ITransformerProvider getTransformerProvider() { return this; }
    @Override public IClassTracker getClassTracker() { return this; }
    @Override public IMixinAuditTrail getAuditTrail() { return null; }
    @Override public IFeatureValidator getFeatureValidator() { return IFeatureValidator.ALLOW_ALL; }
    @Override public IAdviceProvider getAdviceProvider() { return IAdviceProvider.GENERIC; }
    @Override public void offer(IMixinInternal internal) {
        super.offer(internal);
        if (internal instanceof IMixinTransformerFactory factory) {
            transformer = factory.createTransformer();
        }
    }
    @Override public URL[] getClassPath() {
        List<URL> urls = new ArrayList<>(MixinClassPath.containers());
        try { urls.add(VantaMixinService.class.getProtectionDomain().getCodeSource().getLocation()); }
        catch (Exception ignored) { }
        return urls.toArray(URL[]::new);
    }
    @Override public Class<?> findClass(String name) throws ClassNotFoundException {
        return findClass(name, false);
    }
    @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try { return Class.forName(name, initialize, context); }
            catch (ClassNotFoundException exception) { failure = exception; }
        }
        for (ClassLoader loader : MixinClassPath.loaders()) {
            try { return Class.forName(name, initialize, loader); }
            catch (ClassNotFoundException exception) { failure = exception; }
        }
        try { return Class.forName(name, initialize, VantaMixinService.class.getClassLoader()); }
        catch (ClassNotFoundException exception) {
            if (failure != null) exception.addSuppressed(failure);
            throw exception;
        }
    }
    @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, VantaMixinService.class.getClassLoader());
    }
    @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, false, 0);
    }
    @Override public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }
    @Override public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream input = openClassResource(resource)) {
            if (input == null) throw new ClassNotFoundException(name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, readerFlags);
            return node;
        }
    }
    private InputStream openClassResource(String resource) {
        for (ClassLoader loader : MixinClassPath.loaders()) {
            InputStream stream = loader.getResourceAsStream(resource);
            if (stream != null) return stream;
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            InputStream stream = context.getResourceAsStream(resource);
            if (stream != null) return stream;
        }
        return ClassLoader.getSystemResourceAsStream(resource);
    }
    @Override public void registerInvalidClass(String className) { }
    @Override public boolean isClassLoaded(String className) {
        // Avoid forcing class definition merely to answer a tracker query.
        return false;
    }
    @Override public String getClassRestrictions(String className) { return ""; }
    @Override public Collection<ITransformer> getTransformers() {
        return List.of();
    }
    @Override public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
    @Override public void addTransformerExclusion(String name) { }
    @Override public ILogger getLogger(String name) { return super.getLogger(name); }

    public static byte[] transform(String binaryName, byte[] original) {
        IMixinTransformer active = transformer;
        if (active == null) return original;
        return active.transformClass(MixinEnvironment.getCurrentEnvironment(), binaryName, original);
    }

    public static boolean isInitialized() { return transformer != null; }
}
