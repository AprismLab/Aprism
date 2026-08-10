package com.aprism.loader.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.ai.AiAssistant;
import com.aprism.api.ai.AiRequest;
import com.aprism.api.ai.AiResponse;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the AI support reference (v26.3-Alpha.4, goal #8; experimental):
 * {@link AiRegistry} registration, capability-gated completion, refusal
 * semantics, and the runtime wiring ({@code AprismRuntime.getAiRegistry()}).
 *
 * @author BlockConnect@StarsailsClover
 */
class AiRegistryTest {

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    /** Configurable stub assistant. */
    static final class StubAssistant implements AiAssistant {
        private final String name;
        private volatile boolean available;
        private final boolean throwOnComplete;

        StubAssistant(String name, boolean available, boolean throwOnComplete) {
            this.name = name;
            this.available = available;
            this.throwOnComplete = throwOnComplete;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String model() {
            return name + "-model";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public AiResponse complete(AiRequest request) {
            if (throwOnComplete) {
                throw new RuntimeException("synthetic completion failure");
            }
            return new AiResponse("echo: " + request.prompt(), model(),
                    request.prompt().length(), request.prompt().length() + 3, "stop");
        }
    }

    @Nested
    class Registration {

        @Test
        void registerAndLookup() {
            AiRegistry registry = new AiRegistry();
            AiAssistant assistant = new StubAssistant("stub", true, false);

            registry.register(assistant);

            assertThat(registry.get("stub")).contains(assistant);
            assertThat(registry.getAssistantNames()).containsExactly("stub");
        }

        @Test
        void duplicateNameRejected() {
            AiRegistry registry = new AiRegistry();
            registry.register(new StubAssistant("stub", true, false));

            assertThatThrownBy(() -> registry.register(new StubAssistant("stub", true, false)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void unknownAssistantReturnsEmpty() {
            AiRegistry registry = new AiRegistry();
            assertThat(registry.get("ghost")).isEmpty();
        }
    }

    @Nested
    class CapabilityGating {

        @Test
        void availableAssistantNamesFiltered() {
            AiRegistry registry = new AiRegistry();
            registry.register(new StubAssistant("up", true, false));
            registry.register(new StubAssistant("down", false, false));

            assertThat(registry.getAvailableAssistantNames()).containsExactly("up");
        }

        @Test
        void completeUnknownAssistantRefused() {
            AiRegistry registry = new AiRegistry();

            AiResponse response = registry.complete("ghost", AiRequest.of("hi"));

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.finishReason()).contains("not registered");
        }

        @Test
        void completeUnavailableAssistantRefused() {
            AiRegistry registry = new AiRegistry();
            registry.register(new StubAssistant("down", false, false));

            AiResponse response = registry.complete("down", AiRequest.of("hi"));

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.finishReason()).contains("unavailable");
        }

        @Test
        void completeAvailableAssistantSucceeds() {
            AiRegistry registry = new AiRegistry();
            registry.register(new StubAssistant("up", true, false));

            AiResponse response = registry.complete("up", AiRequest.of("hello"));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.text()).isEqualTo("echo: hello");
            assertThat(response.model()).isEqualTo("up-model");
        }

        @Test
        void throwingAssistantReturnsRefusalNotException() {
            AiRegistry registry = new AiRegistry();
            registry.register(new StubAssistant("bad", true, true));

            AiResponse response = registry.complete("bad", AiRequest.of("hi"));

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.finishReason()).contains("synthetic completion failure");
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesAiRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");

            AiRegistry registry = runtime.getAiRegistry();
            assertThat(registry).isNotNull();
            registry.register(new StubAssistant("stub", true, false));
            assertThat(registry.get("stub")).isPresent();
        }

        @Test
        void aiRegistryClearedOnShutdown() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");
            AiRegistry registry = runtime.getAiRegistry();
            registry.register(new StubAssistant("stub", true, false));

            runtime.shutdown();

            assertThat(runtime.getAiRegistry().getAssistantNames()).isEmpty();
        }
    }
}
