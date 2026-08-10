package com.aprism.loader.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.registry.BlockContent;
import com.aprism.api.registry.EntityContent;
import com.aprism.api.registry.ItemContent;
import com.aprism.api.registry.ResourceKey;
import com.aprism.api.registry.TypedRegistry;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the typed registry binding (v26.3-Alpha.2, QA0 gap #2):
 * {@link ResourceKey} parsing/validation, {@link TypedRegistryImpl}
 * semantics, content-record validation, and the runtime wiring
 * ({@code AprismRuntime.getGameRegistries()}).
 *
 * @author BlockConnect@StarsailsClover
 */
class TypedRegistryTest {

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class ResourceKeys {

        @Test
        void parseValidCombinedForm() {
            ResourceKey key = ResourceKey.parse("examplemod:ruby_block");

            assertThat(key.namespace()).isEqualTo("examplemod");
            assertThat(key.name()).isEqualTo("ruby_block");
            assertThat(key.combined()).isEqualTo("examplemod:ruby_block");
        }

        @Test
        void parseRejectsMalformedInput() {
            assertThatThrownBy(() -> ResourceKey.parse("no-colon"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ResourceKey.parse(":name"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ResourceKey.parse("namespace:"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ResourceKey.parse(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void segmentsMustBeLowercaseIdentifiers() {
            assertThatThrownBy(() -> new ResourceKey("Example", "block"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ResourceKey("examplemod", "Ruby"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ResourceKey("", "block"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void allowsUnderscoresAndHyphens() {
            ResourceKey key = new ResourceKey("example-mod", "ruby_block");
            assertThat(key.combined()).isEqualTo("example-mod:ruby_block");
        }
    }

    @Nested
    class ContentValidation {

        @Test
        void blockLuminanceBoundsEnforced() {
            assertThatThrownBy(() -> new BlockContent(
                    ResourceKey.parse("m:b"), 1.0f, 1.0f, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BlockContent(
                    ResourceKey.parse("m:b"), 1.0f, 1.0f, 16))
                    .isInstanceOf(IllegalArgumentException.class);
            BlockContent valid = new BlockContent(ResourceKey.parse("m:b"), 1.0f, 1.0f, 15);
            assertThat(valid.luminance()).isEqualTo(15);
        }

        @Test
        void itemStackBoundsEnforced() {
            assertThatThrownBy(() -> new ItemContent(ResourceKey.parse("m:i"), 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ItemContent(ResourceKey.parse("m:i"), 65))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new ItemContent(ResourceKey.parse("m:i"), 64).maxStack()).isEqualTo(64);
        }

        @Test
        void entityFactoryClassRequired() {
            assertThatThrownBy(() -> new EntityContent(
                    ResourceKey.parse("m:e"), null, true))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EntityContent(
                    ResourceKey.parse("m:e"), "  ", true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RegistrySemantics {

        @Test
        void registerAndLookup() {
            TypedRegistryImpl<BlockContent> registry = new TypedRegistryImpl<>();
            BlockContent content = new BlockContent(
                    ResourceKey.parse("examplemod:ruby"), 2.0f, 6.0f, 0);

            registry.register(content.id(), content);

            assertThat(registry.get(content.id())).contains(content);
            assertThat(registry.contains(content.id())).isTrue();
            assertThat(registry.size()).isEqualTo(1);
        }

        @Test
        void duplicateRegistrationRejected() {
            TypedRegistryImpl<BlockContent> registry = new TypedRegistryImpl<>();
            BlockContent content = new BlockContent(
                    ResourceKey.parse("m:dup"), 1.0f, 1.0f, 0);
            registry.register(content.id(), content);

            assertThatThrownBy(() -> registry.register(content.id(), content))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void nullKeyOrEntryRejected() {
            TypedRegistryImpl<ItemContent> registry = new TypedRegistryImpl<>();
            ItemContent content = new ItemContent(ResourceKey.parse("m:i"), 16);

            assertThatThrownBy(() -> registry.register(null, content))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> registry.register(content.id(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void keysPreserveRegistrationOrder() {
            TypedRegistryImpl<ItemContent> registry = new TypedRegistryImpl<>();
            registry.register(ResourceKey.parse("m:b"), new ItemContent(ResourceKey.parse("m:b"), 1));
            registry.register(ResourceKey.parse("m:a"), new ItemContent(ResourceKey.parse("m:a"), 1));
            registry.register(ResourceKey.parse("m:c"), new ItemContent(ResourceKey.parse("m:c"), 1));

            assertThat(registry.keys()).extracting(ResourceKey::name)
                    .containsExactly("b", "a", "c");
        }

        @Test
        void absentKeyReturnsEmpty() {
            TypedRegistryImpl<EntityContent> registry = new TypedRegistryImpl<>();

            assertThat(registry.get(ResourceKey.parse("m:ghost"))).isEmpty();
            assertThat(registry.contains(ResourceKey.parse("m:ghost"))).isFalse();
        }
    }

    @Nested
    class GameRegistriesHolder {

        @Test
        void exposesThreeTypedRegistries() {
            GameRegistries registries = new GameRegistries();
            ResourceKey blockKey = ResourceKey.parse("m:ruby");
            ResourceKey itemKey = ResourceKey.parse("m:ruby_item");
            ResourceKey entityKey = ResourceKey.parse("m:sprite");

            registries.blocks().register(blockKey, new BlockContent(blockKey, 2.0f, 6.0f, 0));
            registries.items().register(itemKey, new ItemContent(itemKey, 64));
            registries.entities().register(entityKey,
                    new EntityContent(entityKey, "com.example.SpriteEntity", true));

            assertThat(registries.blocks().size()).isEqualTo(1);
            assertThat(registries.items().size()).isEqualTo(1);
            assertThat(registries.entities().size()).isEqualTo(1);

            registries.clear();

            assertThat(registries.blocks().size()).isZero();
            assertThat(registries.items().size()).isZero();
            assertThat(registries.entities().size()).isZero();
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesGameRegistries() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");

            GameRegistries registries = runtime.getGameRegistries();
            assertThat(registries).isNotNull();

            ResourceKey key = ResourceKey.parse("examplemod:ruby");
            registries.blocks().register(key, new BlockContent(key, 2.0f, 6.0f, 0));
            assertThat(registries.blocks().get(key)).isPresent();
        }

        @Test
        void registriesClearedOnShutdown() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");
            GameRegistries registries = runtime.getGameRegistries();
            ResourceKey key = ResourceKey.parse("m:b");
            registries.blocks().register(key, new BlockContent(key, 1.0f, 1.0f, 0));
            assertThat(registries.blocks().size()).isEqualTo(1);

            runtime.shutdown();

            // The same holder instance survives shutdown but is emptied.
            assertThat(runtime.getGameRegistries().blocks().size()).isZero();
        }
    }
}
