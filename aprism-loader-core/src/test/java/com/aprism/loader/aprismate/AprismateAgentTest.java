package com.aprism.loader.aprismate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.aprismate.AprismateAgentDescriptor;
import com.aprism.api.aprismate.AprismateCapability;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the AprismateAgent loader-side reference (v26.4-Alpha.6):
 * runtime detection, capability assembly, descriptor queries, and runtime
 * wiring. Detection uses the {@code aprismate.jdk.version} system
 * property; tests set/clear it per case and restore it afterwards.
 *
 * @author BlockConnect@StarsailsClover
 */
class AprismateAgentTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(AprismateAgent.APRISMATE_VERSION_PROPERTY);
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class Detection {

        @Test
        void stockJvmReportsNotPresent() {
            System.clearProperty(AprismateAgent.APRISMATE_VERSION_PROPERTY);

            AprismateAgent agent = new AprismateAgent(null);

            assertThat(agent.isAprismJdk()).isFalse();
            assertThat(agent.descriptor().present()).isFalse();
            assertThat(agent.descriptor().runtimeName()).isEqualTo("stock");
        }

        @Test
        void aprismJdkPropertyMarksCapableRuntime() {
            System.setProperty(AprismateAgent.APRISMATE_VERSION_PROPERTY, "26.0.0");

            AprismateAgent agent = new AprismateAgent(null);

            assertThat(agent.isAprismJdk()).isTrue();
            assertThat(agent.descriptor().present()).isTrue();
            assertThat(agent.descriptor().runtimeName()).isEqualTo("AprismJDK");
        }
    }

    @Nested
    class CapabilityAssembly {

        @Test
        void nullInstrumentationDowngradesInstrumentationCapabilities() {
            AprismateAgent agent = new AprismateAgent(null);

            AprismateAgentDescriptor descriptor = agent.descriptor();
            assertThat(descriptor.hasCapability(AprismateAgent.CAP_CLASS_REDEFINITION))
                    .isFalse();
            assertThat(descriptor.hasCapability(AprismateAgent.CAP_METHOD_HOOKS)).isFalse();
            assertThat(descriptor.hasCapability(AprismateAgent.CAP_JVM_INTROSPECTION))
                    .isTrue();
            assertThat(descriptor.hasCapability(AprismateAgent.CAP_NATIVE_BRIDGE)).isTrue();
        }

        @Test
        void allCapabilitiesAreAlwaysReported() {
            AprismateAgent agent = new AprismateAgent(null);

            assertThat(agent.descriptor().capabilities()).hasSize(4);
            assertThat(agent.descriptor().availableCapabilityNames())
                    .containsExactlyInAnyOrder(
                            AprismateAgent.CAP_JVM_INTROSPECTION,
                            AprismateAgent.CAP_NATIVE_BRIDGE);
        }

        @Test
        void descriptorAvailableNamesAreConsistent() {
            AprismateAgent agent = new AprismateAgent(null);
            AprismateAgentDescriptor descriptor = agent.descriptor();

            for (String name : descriptor.availableCapabilityNames()) {
                assertThat(descriptor.hasCapability(name)).isTrue();
            }
        }
    }

    @Nested
    class ValueValidation {

        @Test
        void capabilityRejectsBlankName() {
            assertThatThrownBy(() -> new AprismateCapability("", true, ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void capabilityNullDetailBecomesEmpty() {
            AprismateCapability capability = new AprismateCapability("cap", true, null);

            assertThat(capability.detail()).isEmpty();
        }

        @Test
        void capabilityFactoryOmitsDetail() {
            AprismateCapability capability = AprismateCapability.of("cap", false);

            assertThat(capability.available()).isFalse();
            assertThat(capability.detail()).isEmpty();
        }

        @Test
        void descriptorRejectsBlankRuntimeName() {
            assertThatThrownBy(() -> new AprismateAgentDescriptor(true, "", List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesDescriptorAfterInitialize() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "v26.4-Alpha.6", "JE", "26.2");

            AprismateAgentDescriptor descriptor = runtime.getAprismateDescriptor();
            assertThat(descriptor).isNotNull();
            assertThat(descriptor.present()).isFalse();
            assertThat(descriptor.hasCapability(AprismateAgent.CAP_JVM_INTROSPECTION))
                    .isTrue();
        }

        @Test
        void runtimeDescriptorIsNullBeforeInitialize() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.shutdown();

            assertThat(runtime.getAprismateDescriptor()).isNull();
        }
    }
}
