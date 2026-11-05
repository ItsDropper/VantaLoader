package dev.itsdropper.vantaloader.api.accessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessorFactoryTest {
    @Test
    void readsAndWritesPrivateFieldsUsingInferredAndExplicitNames() {
        Target target = new Target();
        View view = AccessorFactory.create(View.class, target);

        assertEquals("initial", view.getName());
        view.setName("updated");
        assertEquals("updated", target.name);
        assertEquals(7, view.readCounter());
    }

    @Test
    void invokesPrivateMethodsAndSupportsOverloads() {
        Target target = new Target();
        Invokers invokers = AccessorFactory.create(Invokers.class, target);

        assertEquals("hello", invokers.invokeFormat("hello"));
        assertEquals("count=4", invokers.invokeFormat(4));
    }

    @Test
    void rejectsInvalidAccessorDeclarations() {
        assertThrows(IllegalArgumentException.class,
                () -> AccessorFactory.create(InvalidView.class, new Target()));
        assertThrows(IllegalArgumentException.class,
                () -> AccessorFactory.create(View.class, new Object()));
    }

    interface View {
        @Accessor String getName();
        @Accessor void setName(String name);
        @Accessor("counter") int readCounter();
    }

    interface Invokers {
        @Invoker String invokeFormat(String value);
        @Invoker("format") String invokeFormat(int value);
    }

    interface InvalidView {
        String missingAnnotation();
    }

    private static final class Target {
        private String name = "initial";
        private int counter = 7;
        private String format(String value) { return value; }
        private String format(int value) { return "count=" + value; }
    }
}
