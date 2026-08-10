package com.aprism.loader.foreignlang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.foreignlang.ForeignBinding;
import com.aprism.api.foreignlang.ForeignSignature;
import com.aprism.api.foreignlang.ForeignType;
import com.aprism.api.foreignlang.OwnershipPolicy;
import com.aprism.api.nativebridge.NativeResult;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the cross-language runtime (v26.4-Alpha.8, Cpp2Java /
 * Rust2Java reference): ABI-mapping vocabulary, signature validation,
 * binding registration, capability-gated invocation through the native
 * bridge seam, and runtime wiring.
 *
 * @author BlockConnect@StarsailsClover
 */
class CrossLanguageRuntimeTest {

    private static ForeignSignature signature(String name) {
        return new ForeignSignature(name, List.of(ForeignType.I32, ForeignType.I32),
                ForeignType.I32);
    }

    private static ForeignBinding binding(String id) {
        return new ForeignBinding(id, "libfoo", "add", signature("add"),
                OwnershipPolicy.ARENA_SCOPED, ForeignBinding.SourceLanguage.CPP);
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class AbiVocabulary {

        @Test
        void parameterTypeRejectsVoid() {
            assertThatThrownBy(() -> new ForeignSignature("fn", List.of(ForeignType.VOID),
                    ForeignType.I32))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid parameter type");
        }

        @Test
        void allTypesAreValidReturnsExceptNone() {
            for (ForeignType type : ForeignType.values()) {
                assertThat(type.isReturnType()).isTrue();
            }
            assertThat(ForeignType.VOID.isParameterType()).isFalse();
            assertThat(ForeignType.I64.isParameterType()).isTrue();
        }

        @Test
        void signatureArityIsComputed() {
            assertThat(signature("add").arity()).isEqualTo(2);
            assertThat(new ForeignSignature("noop", List.of(), ForeignType.VOID).arity())
                    .isEqualTo(0);
        }
    }

    @Nested
    class ValueValidation {

        @Test
        void bindingRejectsBlankFields() {
            assertThatThrownBy(() -> new ForeignBinding("", "lib", "sym", signature("f"),
                    OwnershipPolicy.CALLEE_OWNS, ForeignBinding.SourceLanguage.RUST))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ForeignBinding("id", "", "sym", signature("f"),
                    OwnershipPolicy.CALLEE_OWNS, ForeignBinding.SourceLanguage.RUST))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void bindingRejectsNullCollaborators() {
            assertThatThrownBy(() -> new ForeignBinding("id", "lib", "sym", null,
                    OwnershipPolicy.CALLEE_OWNS, ForeignBinding.SourceLanguage.RUST))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ForeignBinding("id", "lib", "sym", signature("f"),
                    null, ForeignBinding.SourceLanguage.RUST))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ForeignBinding("id", "lib", "sym", signature("f"),
                    OwnershipPolicy.CALLEE_OWNS, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void signatureRejectsBlankNameAndNullReturn() {
            assertThatThrownBy(() -> new ForeignSignature("", List.of(), ForeignType.VOID))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ForeignSignature("fn", List.of(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BindingRegistry {

        @Test
        void registerAndLookUpBindings() {
            AprismRuntime runtime = AprismRuntime.instance();
            CrossLanguageRuntime cross = runtime.getCrossLanguageRuntime();
            cross.registerBinding(binding("cpp:libfoo.add"));

            assertThat(cross.getBindingIds()).containsExactly("cpp:libfoo.add");
            assertThat(cross.getBinding("cpp:libfoo.add")).isPresent();
        }

        @Test
        void duplicateBindingIsRejected() {
            AprismRuntime runtime = AprismRuntime.instance();
            CrossLanguageRuntime cross = runtime.getCrossLanguageRuntime();
            cross.registerBinding(binding("cpp:libfoo.add"));

            assertThatThrownBy(() -> cross.registerBinding(binding("cpp:libfoo.add")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void unknownBindingLookupIsEmpty() {
            AprismRuntime runtime = AprismRuntime.instance();
            CrossLanguageRuntime cross = runtime.getCrossLanguageRuntime();

            assertThat(cross.getBinding("missing")).isEmpty();
        }
    }

    @Nested
    class CapabilityGatedInvocation {

        @Test
        void unknownBindingIsRefused() {
            AprismRuntime runtime = AprismRuntime.instance();
            CrossLanguageRuntime cross = runtime.getCrossLanguageRuntime();

            NativeResult result = cross.invoke("missing", "ffm", 1, 2);

            assertThat(result.success()).isFalse();
            assertThat(result.reason()).contains("binding not registered");
        }

        @Test
        void invokeWithoutNativeProviderIsRefused() {
            AprismRuntime runtime = AprismRuntime.instance();
            CrossLanguageRuntime cross = runtime.getCrossLanguageRuntime();
            cross.registerBinding(binding("cpp:libfoo.add"));

            // No native bridge provider registered: symbol resolution is
            // refused, and the refusal propagates as a refusal (never thrown).
            NativeResult result = cross.invoke("cpp:libfoo.add", "ffm", 1, 2);

            assertThat(result.success()).isFalse();
            assertThat(result.reason()).contains("symbol resolution failed");
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesCrossLanguageRuntime() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getCrossLanguageRuntime()).isNotNull();
            assertThat(runtime.getCrossLanguageRuntime())
                    .isSameAs(runtime.getCrossLanguageRuntime());
        }

        @Test
        void runtimeShutdownClearsBindings() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.getCrossLanguageRuntime().registerBinding(binding("cpp:libfoo.add"));

            runtime.shutdown();

            assertThat(runtime.getCrossLanguageRuntime().getBindingIds()).isEmpty();
        }
    }
}
