package com.aprism.loader.imc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.imc.ImcMessage;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the inter-mod communication surface (v26.3-Alpha.7,
 * Forge/NeoForge InterModComms parity): phase gating, queue drain
 * semantics, method-key filtering, and runtime wiring.
 *
 * @author BlockConnect@StarsailsClover
 */
class InterModCommsImplTest {

    private final InterModCommsImpl comms = new InterModCommsImpl();

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class PhaseGating {

        @Test
        void sendBeforeInitPhaseIsRejected() {
            assertThatThrownBy(() -> comms.sendTo("sender", "target", "method", "payload"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("before INIT phase");
        }

        @Test
        void sendAfterInitPhaseIsAccepted() {
            comms.markInitPhaseReached();

            assertThat(comms.sendTo("sender", "target", "method", "payload")).isTrue();
            assertThat(comms.hasMessages("target")).isTrue();
        }

        @Test
        void clearResetsTheSendWindow() {
            comms.markInitPhaseReached();
            comms.clear();

            assertThat(comms.isSendWindowOpen()).isFalse();
            assertThatThrownBy(() -> comms.sendTo("sender", "target", "method", "payload"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class MessageValidation {

        @Test
        void blankAddressingFieldsAreRejected() {
            assertThatThrownBy(() -> new ImcMessage("", "method", "sender", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ImcMessage("target", "", "sender", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ImcMessage("target", "method", "", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class DrainSemantics {

        @Test
        void getMessagesDrainsTheQueue() {
            comms.markInitPhaseReached();
            comms.sendTo("a", "target", "m1", "p1");
            comms.sendTo("b", "target", "m2", "p2");

            List<ImcMessage> first = comms.getMessages("target");
            List<ImcMessage> second = comms.getMessages("target");

            assertThat(first).hasSize(2);
            assertThat(first.get(0).senderModId()).isEqualTo("a");
            assertThat(first.get(1).payload()).isEqualTo("p2");
            assertThat(second).isEmpty();
        }

        @Test
        void messagesPreserveSendOrder() {
            comms.markInitPhaseReached();
            for (int i = 0; i < 5; i++) {
                comms.sendTo("sender", "target", "m" + i, i);
            }

            List<ImcMessage> drained = comms.getMessages("target");

            assertThat(drained).extracting(ImcMessage::methodKey)
                    .containsExactly("m0", "m1", "m2", "m3", "m4");
        }

        @Test
        void unknownRecipientReturnsEmptyList() {
            assertThat(comms.getMessages("nobody")).isEmpty();
            assertThat(comms.hasMessages("nobody")).isFalse();
        }
    }

    @Nested
    class MethodKeyFiltering {

        @Test
        void filterDrainsOnlyMatchingMethod() {
            comms.markInitPhaseReached();
            comms.sendTo("a", "target", "register", "r1");
            comms.sendTo("b", "target", "configure", "c1");
            comms.sendTo("c", "target", "register", "r2");

            List<ImcMessage> matched = comms.getMessages("target", "register");
            List<ImcMessage> remaining = comms.getMessages("target");

            assertThat(matched).extracting(ImcMessage::methodKey)
                    .containsExactly("register", "register");
            assertThat(matched).extracting(ImcMessage::payload)
                    .containsExactly("r1", "r2");
            assertThat(remaining).extracting(ImcMessage::methodKey)
                    .containsExactly("configure");
        }

        @Test
        void nullFilterReturnsEmptyWithoutDraining() {
            comms.markInitPhaseReached();
            comms.sendTo("a", "target", "method", "payload");

            assertThat(comms.getMessages("target", null)).isEmpty();
            assertThat(comms.hasMessages("target")).isTrue();
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesTheImcSurface() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getInterModComms()).isNotNull();
            assertThat(runtime.getInterModComms()).isSameAs(runtime.getInterModComms());
        }

        @Test
        void runtimeShutdownClearsTheImcWindow() {
            AprismRuntime runtime = AprismRuntime.instance();
            InterModCommsImpl impl = (InterModCommsImpl) runtime.getInterModComms();
            impl.markInitPhaseReached();
            impl.sendTo("sender", "target", "method", "payload");

            runtime.shutdown();

            InterModCommsImpl fresh = (InterModCommsImpl) runtime.getInterModComms();
            assertThat(fresh.isSendWindowOpen()).isFalse();
            assertThat(fresh.hasMessages("target")).isFalse();
        }
    }
}
