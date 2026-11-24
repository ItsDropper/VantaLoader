package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ServiceKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModServiceRegistryTest {
    private static final ServiceKey<Runnable> GREETER =
            ServiceKey.of("test:greeter", Runnable.class);

    @Test
    void servicesAreSharedAcrossModScopesAndRemovedWithTheirOwner() {
        ModServiceRegistry registry = new ModServiceRegistry();
        ModServiceRegistry.Scope provider = registry.createScope("provider");
        ModServiceRegistry.Scope consumer = registry.createScope("consumer");
        int[] calls = {0};

        var registration = provider.register(GREETER, () -> calls[0]++);
        assertSame(registration, registration);
        consumer.require(GREETER).run();

        assertEquals(1, calls[0]);
        assertTrue(consumer.isRegistered(GREETER));

        provider.close();

        assertTrue(registration.isClosed());
        assertFalse(consumer.isRegistered(GREETER));
        assertThrows(IllegalStateException.class, () -> provider.find(GREETER));
        consumer.close();
    }

    @Test
    void rejectsDuplicateIdsAndMismatchedServiceTypes() {
        ModServiceRegistry registry = new ModServiceRegistry();
        var provider = registry.createScope("provider");
        var consumer = registry.createScope("consumer");
        provider.register(GREETER, () -> { });

        assertThrows(IllegalStateException.class,
                () -> consumer.register(GREETER, () -> { }));
        assertThrows(IllegalStateException.class,
                () -> consumer.find(ServiceKey.of("test:greeter", String.class)));

        provider.close();
        consumer.close();
    }

    @Test
    void validatesServiceInstancesAndRegistrationRemovalIsIdempotent() {
        ModServiceRegistry registry = new ModServiceRegistry();
        var scope = registry.createScope("provider");

        assertThrows(NullPointerException.class, () -> scope.register(GREETER, null));
        var registration = scope.register(GREETER, () -> { });
        registration.close();
        registration.close();

        assertTrue(registration.isClosed());
        assertFalse(scope.isRegistered(GREETER));
        scope.close();
    }

    @Test
    void closingAConsumerScopePreventsFurtherDiscovery() {
        ModServiceRegistry registry = new ModServiceRegistry();
        var provider = registry.createScope("provider");
        var consumer = registry.createScope("consumer");
        provider.register(GREETER, () -> { });

        consumer.close();

        assertThrows(IllegalStateException.class, () -> consumer.find(GREETER));
        provider.close();
    }
}
