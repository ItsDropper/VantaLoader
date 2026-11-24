package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ServiceKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModServiceRegistryTest {
    private static final ServiceKey<Runnable> GREETER =
            ServiceKey.of("provider:greeter", Runnable.class);

    @Test
    void servicesAreSharedAcrossModScopesAndRemovedWithTheirOwner() {
        ModServiceRegistry registry = new ModServiceRegistry();
        ModServiceRegistry.Scope provider = registry.createScope("provider");
        ModServiceRegistry.Scope consumer = registry.createScope("consumer");
        int[] calls = {0};

        var registration = provider.register(GREETER, () -> calls[0]++);
        assertEquals(GREETER, registration.key());
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
        var duplicateProvider = registry.createScope("provider");
        var consumer = registry.createScope("consumer");
        provider.register(GREETER, () -> { });

        assertThrows(IllegalStateException.class,
                () -> duplicateProvider.register(GREETER, () -> { }));
        assertThrows(IllegalStateException.class,
                () -> consumer.find(ServiceKey.of("provider:greeter", String.class)));

        provider.close();
        duplicateProvider.close();
        consumer.close();
    }

    @Test
    void validatesServiceInstancesAndRegistrationRemovalIsIdempotent() {
        ModServiceRegistry registry = new ModServiceRegistry();
        var scope = registry.createScope("provider");

        assertThrows(NullPointerException.class, () -> scope.register(GREETER, null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.createScope("other").register(GREETER, () -> { }));
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
