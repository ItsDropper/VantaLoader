package dev.itsdropper.vantaloader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HierarchyClassWriterTest {
    private final HierarchyClassWriter writer = new HierarchyClassWriter(
            0, getClass().getClassLoader());

    @Test
    void resolvesCommonSuperclassWithoutDefiningGameClasses() {
        assertEquals(internal(Base.class), writer.commonSuperClass(internal(Alpha.class), internal(Beta.class)));
    }

    @Test
    void resolvesInheritedInterfaceAncestry() {
        assertEquals(internal(Root.class), writer.commonSuperClass(internal(Left.class), internal(Root.class)));
        assertEquals(internal(Root.class), writer.commonSuperClass(internal(Alpha.class), internal(Root.class)));
    }

    @Test
    void resolvesReferenceAndMultidimensionalArrayMerges() {
        assertEquals("[[Ljava/lang/Object;", writer.commonSuperClass(
                "[[Ljava/lang/String;", "[[Ljava/lang/Integer;"));
        assertEquals("[Ljava/lang/Object;", writer.commonSuperClass(
                "[Ljava/lang/String;", "[Ljava/lang/Integer;"));
    }

    @Test
    void resolvesPrimitiveArrayAndArrayMarkerTypes() {
        assertEquals("java/lang/Object", writer.commonSuperClass("[I", "[J"));
        assertEquals("java/lang/Cloneable", writer.commonSuperClass("[I", "java/lang/Cloneable"));
        assertEquals("java/lang/Object", writer.commonSuperClass("[I", "java/lang/String"));
    }

    @Test
    void preservesAssignableTypesAndUnknownClassFallback() {
        assertEquals(internal(Base.class), writer.commonSuperClass(internal(Base.class), internal(Alpha.class)));
        assertEquals("java/lang/Object", writer.commonSuperClass("missing/First", "missing/Second"));
    }

    private static String internal(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    interface Root { }
    interface Left extends Root { }
    static class Base { }
    static final class Alpha extends Base implements Left { }
    static final class Beta extends Base implements Root { }
}
