package com.aprism.loader.lowlevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.lowlevel.ClassShape;
import com.aprism.loader.AprismClassTransformer;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the load-time class observer registry and its transformer
 * pipeline wiring (v26.4-Alpha.3, deep bytecode-hook API).
 *
 * @author BlockConnect@StarsailsClover
 */
class ClassLoadObserverRegistryTest {

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    private static ClassShape sampleShape(String name) {
        return new ClassShape(name, "java/lang/Object", List.of(), 1, List.of(), List.of());
    }

    @Nested
    class Registration {

        @Test
        void registerAndListObservers() {
            ClassLoadObserverRegistry registry = new ClassLoadObserverRegistry();
            ClassShapeObserver observer = new ClassShapeObserver();

            registry.register(observer);

            assertThat(registry.registeredObservers()).containsExactly(observer);
        }

        @Test
        void nullObserverIsRejected() {
            ClassLoadObserverRegistry registry = new ClassLoadObserverRegistry();

            assertThatThrownBy(() -> registry.register(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void duplicateObserverIsRejected() {
            ClassLoadObserverRegistry registry = new ClassLoadObserverRegistry();
            ClassShapeObserver observer = new ClassShapeObserver();
            registry.register(observer);

            assertThatThrownBy(() -> registry.register(observer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void unregisterRemovesObserver() {
            ClassLoadObserverRegistry registry = new ClassLoadObserverRegistry();
            ClassShapeObserver observer = new ClassShapeObserver();
            registry.register(observer);

            assertThat(registry.unregister(observer)).isTrue();
            assertThat(registry.unregister(observer)).isFalse();
            assertThat(registry.registeredObservers()).isEmpty();
        }

        @Test
        void clearRemovesAllObservers() {
            ClassLoadObserverRegistry registry = new ClassLoadObserverRegistry();
            registry.register(new ClassShapeObserver());
            registry.register(new ClassShapeObserver());

            registry.clear();

            assertThat(registry.registeredObservers()).isEmpty();
        }
    }

    @Nested
    class Notification {

        @Test
        void notifiesEveryObserverInOrder() {
            ClassLoadObserverRegistry registry = new ClassLoadObserverRegistry();
            List<String> fired = new ArrayList<>();
            registry.register(shape -> fired.add("a:" + shape.className()));
            registry.register(shape -> fired.add("b:" + shape.className()));

            registry.notifyObservers(sampleShape("pkg/Foo"));

            assertThat(fired).containsExactly("a:pkg/Foo", "b:pkg/Foo");
        }

        @Test
        void throwingObserverDoesNotAbortOthers() {
            ClassLoadObserverRegistry registry = new ClassLoadObserverRegistry();
            List<String> fired = new ArrayList<>();
            registry.register(shape -> fired.add("before"));
            registry.register(shape -> { throw new RuntimeException("boom"); });
            registry.register(shape -> fired.add("after"));

            registry.notifyObservers(sampleShape("pkg/Foo"));

            assertThat(fired).containsExactly("before", "after");
        }
    }

    @Nested
    class TransformerWiring {

        @Test
        void transformerExposesTheObserverRegistry() {
            AprismClassTransformer transformer = new AprismClassTransformer();

            assertThat(transformer.getClassLoadObservers()).isNotNull();
            assertThat(transformer.getClassLoadObservers().registeredObservers()).isEmpty();
        }

        @Test
        void transformNotifiesObserversFailSafely() throws Exception {
            AprismClassTransformer transformer = new AprismClassTransformer();
            List<String> seen = new ArrayList<>();
            transformer.getClassLoadObservers().register(shape -> seen.add(shape.className()));
            transformer.getClassLoadObservers().register(shape -> {
                throw new RuntimeException("boom");
            });

            byte[] bytes = readClassBytes(ClassLoadObserverRegistryTest.class);
            byte[] result = transformer.transform(null, "com/aprism/loader/lowlevel/ClassLoadObserverRegistryTest", null, null, bytes);

            // A throwing observer must not break the pipeline: bytes are
            // returned unchanged and the good observer still fired.
            assertThat(result).isSameAs(bytes);
            assertThat(seen).containsExactly(
                    "com/aprism/loader/lowlevel/ClassLoadObserverRegistryTest");
        }

        @Test
        void platformClassesAreNeverTransformed() throws Exception {
            // v26.9-Alpha.8: rewriting java.lang.invoke (or any JDK package)
            // from a transformer re-enters class loading mid-transform of
            // MethodHandle and aborts agent load with ClassCircularityError.
            assertThat(AprismClassTransformer.isPlatformClass("java/lang/invoke/MethodHandle$1")).isTrue();
            assertThat(AprismClassTransformer.isPlatformClass("java/lang/String")).isTrue();
            assertThat(AprismClassTransformer.isPlatformClass("jdk/internal/misc/Unsafe")).isTrue();
            assertThat(AprismClassTransformer.isPlatformClass("sun/misc/Unsafe")).isTrue();
            assertThat(AprismClassTransformer.isPlatformClass("net/minecraft/client/Minecraft")).isFalse();
            assertThat(AprismClassTransformer.isPlatformClass("com/aprism/loader/AprismAgent")).isFalse();

            AprismClassTransformer transformer = new AprismClassTransformer();
            byte[] bytes = readClassBytes(ClassLoadObserverRegistryTest.class);
            assertThat(transformer.transform(null, "java/lang/invoke/MethodHandle$1",
                    null, null, bytes)).isNull();
        }

        void transformWithoutObserversIsUnchanged() throws Exception {
            AprismClassTransformer transformer = new AprismClassTransformer();
            byte[] bytes = readClassBytes(ClassLoadObserverRegistryTest.class);

            byte[] result = transformer.transform(null, "com/aprism/loader/lowlevel/ClassLoadObserverRegistryTest", null, null, bytes);

            assertThat(result).isSameAs(bytes);
        }
    }

    private static byte[] readClassBytes(Class<?> clazz) {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        try (var in = ClassLoadObserverRegistryTest.class.getResourceAsStream(resource)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read class bytes", e);
        }
    }

    /** Named observer fixture for registration tests. */
    private static final class ClassShapeObserver
            implements com.aprism.api.lowlevel.ClassLoadObserver {
        @Override
        public void onClassObserved(ClassShape shape) {
            // no-op fixture
        }
    }
}
