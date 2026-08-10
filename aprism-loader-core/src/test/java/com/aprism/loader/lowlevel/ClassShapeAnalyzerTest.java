package com.aprism.loader.lowlevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.lowlevel.ClassShape;
import com.aprism.api.lowlevel.ClassShapeDiff;

/**
 * Tests for the class-shape analyzer (v26.4-Alpha.3, deep bytecode-hook
 * API): bytecode parsing into typed shapes, structural diffs, and the
 * validate-before-redefine workflow.
 *
 * @author BlockConnect@StarsailsClover
 */
class ClassShapeAnalyzerTest {

    @Nested
    class Analysis {

        @Test
        void analyzesARealClassShape() {
            ClassShape shape = ClassShapeAnalyzer.analyze(toBytes(SampleBase.class));

            assertThat(shape.className()).isEqualTo(
                    SampleBase.class.getName().replace('.', '/'));
            assertThat(shape.superName()).isEqualTo("java/lang/Object");
            assertThat(shape.declaresMethod("greet", "()Ljava/lang/String;")).isTrue();
            assertThat(shape.declaresField("name")).isTrue();
            assertThat(shape.declaresField("missing")).isFalse();
            assertThat(shape.methods()).isNotEmpty();
        }

        @Test
        void analyzesInterfacesAndHierarchy() {
            ClassShape shape = ClassShapeAnalyzer.analyze(toBytes(SampleChild.class));

            assertThat(shape.superName()).isEqualTo(
                    SampleBase.class.getName().replace('.', '/'));
            assertThat(shape.interfaces()).contains("java/lang/Runnable");
            assertThat(shape.declaresMethod("run", "()V")).isTrue();
        }

        @Test
        void methodHookFormMatchesRegistryConvention() {
            ClassShape shape = ClassShapeAnalyzer.analyze(toBytes(SampleBase.class));

            ClassShape.MethodShape greet = shape.methods().stream()
                    .filter(m -> m.name().equals("greet"))
                    .findFirst().orElseThrow();

            assertThat(greet.hookForm()).isEqualTo("greet()Ljava/lang/String;");
        }

        @Test
        void nullOrEmptyBytesAreRejected() {
            assertThatThrownBy(() -> ClassShapeAnalyzer.analyze(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClassShapeAnalyzer.analyze(new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void garbageBytesAreRejected() {
            assertThatThrownBy(() -> ClassShapeAnalyzer.analyze(new byte[] {1, 2, 3, 4}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a valid class file");
        }

        @Test
        void blankNamesAreRejectedAtConstruction() {
            assertThatThrownBy(() -> new ClassShape("", null, null, 0, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Diffing {

        @Test
        void identicalShapesProduceEmptyDiff() {
            ClassShape shape = ClassShapeAnalyzer.analyze(toBytes(SampleBase.class));

            ClassShapeDiff diff = ClassShapeAnalyzer.diff(shape, shape);

            assertThat(diff.isEmpty()).isTrue();
            assertThat(diff.isStructural()).isFalse();
        }

        @Test
        void addedMethodIsDetected() {
            ClassShape oldShape = ClassShapeAnalyzer.analyze(toBytes(SampleBase.class));
            List<ClassShape.MethodShape> methods = new ArrayList<>(oldShape.methods());
            methods.add(new ClassShape.MethodShape("extra", "()V", 1));
            ClassShape newShape = new ClassShape(oldShape.className(), oldShape.superName(),
                    oldShape.interfaces(), oldShape.access(), methods, oldShape.fields());

            ClassShapeDiff diff = ClassShapeAnalyzer.diff(oldShape, newShape);

            assertThat(diff.addedMethods()).containsExactly("extra()V");
            assertThat(diff.removedMethods()).isEmpty();
            assertThat(diff.isStructural()).isTrue();
        }

        @Test
        void removedFieldIsDetected() {
            ClassShape oldShape = ClassShapeAnalyzer.analyze(toBytes(SampleBase.class));
            ClassShape newShape = new ClassShape(oldShape.className(), oldShape.superName(),
                    oldShape.interfaces(), oldShape.access(), oldShape.methods(), List.of());

            ClassShapeDiff diff = ClassShapeAnalyzer.diff(oldShape, newShape);

            assertThat(diff.removedFields()).containsExactly("name");
            assertThat(diff.isStructural()).isTrue();
        }

        @Test
        void superclassAndInterfaceChangesAreDetected() {
            ClassShape oldShape = ClassShapeAnalyzer.analyze(toBytes(SampleBase.class));
            ClassShape newShape = new ClassShape(oldShape.className(), "java/lang/Number",
                    List.of("java/lang/Runnable"), oldShape.access(),
                    oldShape.methods(), oldShape.fields());

            ClassShapeDiff diff = ClassShapeAnalyzer.diff(oldShape, newShape);

            assertThat(diff.superclassChanged()).isTrue();
            assertThat(diff.interfacesChanged()).isTrue();
            assertThat(diff.isStructural()).isTrue();
        }

        @Test
        void diffBetweenDistinctClassesIsStructural() {
            ClassShape base = ClassShapeAnalyzer.analyze(toBytes(SampleBase.class));
            ClassShape child = ClassShapeAnalyzer.analyze(toBytes(SampleChild.class));

            ClassShapeDiff diff = ClassShapeAnalyzer.diff(base, child);

            assertThat(diff.isStructural()).isTrue();
            assertThat(diff.superclassChanged()).isTrue();
        }
    }

    private static byte[] toBytes(Class<?> clazz) {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        try (var in = ClassShapeAnalyzerTest.class.getResourceAsStream(resource)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read class bytes", e);
        }
    }

    /** Sample class used as analysis fixture. */
    public static class SampleBase {
        String name;

        String greet() {
            return "hi";
        }
    }

    /** Sample subclass with an interface used as analysis fixture. */
    public static class SampleChild extends SampleBase implements Runnable {
        @Override
        public void run() {
        }
    }
}
