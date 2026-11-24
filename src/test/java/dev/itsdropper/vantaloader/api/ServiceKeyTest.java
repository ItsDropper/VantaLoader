package dev.itsdropper.vantaloader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceKeyTest {
    @Test
    void acceptsNamespacedServiceIdsAndPreservesServiceType() {
        ServiceKey<Runnable> key = ServiceKey.of("profilemod:profiles/v1", Runnable.class);

        assertEquals("profilemod:profiles/v1", key.id());
        assertSame(Runnable.class, key.serviceType());
    }

    @Test
    void rejectsMalformedIdsAndNonReferenceTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> ServiceKey.of("MissingNamespace", Runnable.class));
        assertThrows(IllegalArgumentException.class,
                () -> ServiceKey.of("profilemod:", Runnable.class));
        assertThrows(IllegalArgumentException.class,
                () -> ServiceKey.of("profilemod:profiles", int.class));
        assertThrows(NullPointerException.class,
                () -> ServiceKey.of("profilemod:profiles", null));
    }
}
