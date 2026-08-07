package com.aprism.loader.remap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the tiny v2 mapping parser and the bidirectional remapper.
 *
 * @author BlockConnect@StarsailsClover
 */
class TinyRemapperTest {

    /**
     * A representative tiny v2 mapping fragment: official (ns0) to
     * intermediary (ns1), with an extra yarn namespace (ns2) that must be
     * ignored, properties, comments, and parameters.
     */
    private static final String TINY = """
            tiny\t2\t0\tofficial\tintermediary\tnamed
            \tescaped-names
            c\tnet/minecraft/client/Minecraft\tnet/minecraft/class_310\tMinecraft
            \tc\tThe main game class.
            \tm\t(Ljava/lang/String;)V\tmain\tmethod_1497\tmain
            \t\tp\t0\targ\tstring
            \tm\t()V\tinit\tmethod_1500\tinit
            \tf\tI\tfps\tfield_1687\tfps
            \tf\tZ\trunning\tfield_1690\trunning
            c\tnet/minecraft/world/World\tnet/minecraft/class_1937\tWorld
            \tm\t()V\tinit\tmethod_8300\tinit
            c\tnet/minecraft/screen/ScreenHandler\tnet/minecraft/class_1703
            c\tnet/minecraft/NoSecondName\t
            """;

    private static TinyMappings mappings() {
        try {
            return TinyMappings.parse(new StringReader(TINY));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    class Parser {

        @Test
        void parsesClasses() {
            TinyMappings m = mappings();
            assertThat(m.classCount()).isEqualTo(3);
            assertThat(m.classNamedOf("net/minecraft/client/Minecraft"))
                    .isEqualTo("net/minecraft/class_310");
            assertThat(m.classIntermediaryOf("net/minecraft/class_1937"))
                    .isEqualTo("net/minecraft/world/World");
        }

        @Test
        void parsesMembers() {
            TinyMappings m = mappings();
            assertThat(m.methodCount()).isEqualTo(3);
            assertThat(m.fieldCount()).isEqualTo(2);
            assertThat(m.methodNamedOf("net/minecraft/client/Minecraft",
                    "(Ljava/lang/String;)V", "main"))
                    .isEqualTo("method_1497");
            assertThat(m.fieldNamedOf("net/minecraft/client/Minecraft", "I", "fps"))
                    .isEqualTo("field_1687");
        }

        @Test
        void recordsProperties() {
            TinyMappings m = mappings();
            assertThat(m.properties()).containsEntry("escaped-names", "");
        }

        @Test
        void classWithoutSecondNamespaceIsNotMapped() {
            TinyMappings m = mappings();
            // ScreenHandler maps normally
            assertThat(m.classNamedOf("net/minecraft/screen/ScreenHandler"))
                    .isEqualTo("net/minecraft/class_1703");
            // NoSecondName has an empty namespace-1 name: no class mapping recorded
            assertThat(m.classNamedOf("net/minecraft/NoSecondName")).isNull();
        }

        @Test
        void rejectsNonTinyHeader() {
            assertThatThrownBy(() -> TinyMappings.parse(new StringReader("not a mapping")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsTinyV1Header() {
            assertThatThrownBy(() -> TinyMappings.parse(new StringReader("v1\tofficial\tintermediary\n")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void ignoresCommentsAndParameters() {
            // The fixture contains a class comment and a parameter mapping;
            // parsing must succeed and they must not corrupt member tables.
            TinyMappings m = mappings();
            assertThat(m.methodNamedOf("net/minecraft/client/Minecraft",
                    "(Ljava/lang/String;)V", "main")).isNotNull();
        }
    }

    @Nested
    class ForwardRemap {

        @Test
        void mapsClassForward() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapClassName("net/minecraft/client/Minecraft"))
                    .isEqualTo("net/minecraft/class_310");
        }

        @Test
        void passesThroughUnmappedClass() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapClassName("com/example/Unknown")).isEqualTo("com/example/Unknown");
        }

        @Test
        void mapsMethodForward() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapMethodName("net/minecraft/client/Minecraft",
                    "main", "(Ljava/lang/String;)V")).isEqualTo("method_1497");
        }

        @Test
        void neverRemapsConstructors() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapMethodName("net/minecraft/client/Minecraft",
                    "<init>", "()V")).isEqualTo("<init>");
            assertThat(r.mapMethodName("net/minecraft/client/Minecraft",
                    "<clinit>", "()V")).isEqualTo("<clinit>");
        }

        @Test
        void mapsFieldForward() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapFieldName("net/minecraft/client/Minecraft", "fps", "I"))
                    .isEqualTo("field_1687");
        }

        @Test
        void mapsDescriptorForward() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapDesc("(Lnet/minecraft/client/Minecraft;[Lnet/minecraft/world/World;)V"))
                    .isEqualTo("(Lnet/minecraft/class_310;[Lnet/minecraft/class_1937;)V");
        }
    }

    @Nested
    class ReverseRemap {

        @Test
        void mapsClassReverse() {
            Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
            assertThat(r.mapClassName("net/minecraft/class_310"))
                    .isEqualTo("net/minecraft/client/Minecraft");
        }

        @Test
        void mapsMethodReverse() {
            Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
            // Incoming descriptor is in namespace 1 (intermediary) and must be
            // translated to namespace 0 before the reverse lookup.
            assertThat(r.mapMethodName("net/minecraft/class_310",
                    "method_1497", "(Ljava/lang/String;)V")).isEqualTo("main");
        }

        @Test
        void mapsMethodReverseWithMappedDescriptor() {
            Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
            // Descriptor containing intermediary class references
            assertThat(r.mapMethodName("net/minecraft/class_1937",
                    "method_8300", "()V")).isEqualTo("init");
        }

        @Test
        void mapsFieldReverse() {
            Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
            assertThat(r.mapFieldName("net/minecraft/class_310", "field_1687", "I"))
                    .isEqualTo("fps");
        }

        @Test
        void mapsDescriptorReverse() {
            Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
            assertThat(r.mapDesc("(Lnet/minecraft/class_310;[I)Lnet/minecraft/class_1937;"))
                    .isEqualTo("(Lnet/minecraft/client/Minecraft;[I)Lnet/minecraft/world/World;");
        }
    }

    @Nested
    class DescriptorTranslation {

        @Test
        void primitivesPassThrough() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapDesc("(IJZ)V")).isEqualTo("(IJZ)V");
        }

        @Test
        void nestedArraysOfMappedType() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapDesc("[[[Lnet/minecraft/client/Minecraft;"))
                    .isEqualTo("[[[Lnet/minecraft/class_310;");
        }

        @Test
        void mixedPrimitivesAndObjects() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapDesc("(ILnet/minecraft/world/World;J)Ljava/lang/String;"))
                    .isEqualTo("(ILnet/minecraft/class_1937;J)Ljava/lang/String;");
        }

        @Test
        void emptyAndNullDescriptors() {
            Remapper r = TinyRemapper.officialToIntermediary(mappings());
            assertThat(r.mapDesc("")).isEmpty();
            assertThat(r.mapDesc(null)).isNull();
        }

        @Test
        void noopRemapperPassesEverything() {
            Remapper r = Remapper.noop();
            assertThat(r.mapClassName("net/minecraft/client/Minecraft"))
                    .isEqualTo("net/minecraft/client/Minecraft");
            assertThat(r.mapMethodName("a", "b", "c")).isEqualTo("b");
            assertThat(r.mapFieldName("a", "b", "c")).isEqualTo("b");
            assertThat(r.mapDesc("(La;)V")).isEqualTo("(La;)V");
        }
    }
}
