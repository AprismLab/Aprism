package com.aprism.loader.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.commands.CommandSpec;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the command registration surface (v26.3-Alpha.8, Fabric
 * CommandRegistrationCallback parity): registration-window gating,
 * duplicate rejection, spec validation, and runtime wiring.
 *
 * @author BlockConnect@StarsailsClover
 */
class CommandRegistrationImplTest {

    private final CommandRegistrationImpl registration = new CommandRegistrationImpl();

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class WindowGating {

        @Test
        void registerBeforeWindowOpensIsRejected() {
            assertThatThrownBy(() -> registration.register(
                    new CommandSpec("cmd", "", new Object())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("registration window");
        }

        @Test
        void registerWhileWindowIsOpenIsAccepted() {
            registration.openWindow();

            registration.register(new CommandSpec("cmd", "desc", new Object()));

            assertThat(registration.registeredCommands()).hasSize(1);
            assertThat(registration.registeredCommands().get(0).name()).isEqualTo("cmd");
        }

        @Test
        void registerAfterFreezeIsRejected() {
            registration.openWindow();
            registration.register(new CommandSpec("cmd", "", new Object()));
            registration.freezeWindow();

            assertThat(registration.isFrozen()).isTrue();
            assertThat(registration.isWindowOpen()).isFalse();
            assertThatThrownBy(() -> registration.register(
                    new CommandSpec("other", "", new Object())))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void clearResetsWindowAndFrozenState() {
            registration.openWindow();
            registration.register(new CommandSpec("cmd", "", new Object()));
            registration.freezeWindow();

            registration.clear();

            assertThat(registration.isFrozen()).isFalse();
            assertThat(registration.isWindowOpen()).isFalse();
            assertThat(registration.registeredCommands()).isEmpty();
        }
    }

    @Nested
    class Validation {

        @Test
        void duplicateCommandNameIsRejected() {
            registration.openWindow();
            registration.register(new CommandSpec("cmd", "", new Object()));

            assertThatThrownBy(() -> registration.register(
                    new CommandSpec("cmd", "", new Object())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("duplicate command name");
        }

        @Test
        void blankCommandNameIsRejected() {
            assertThatThrownBy(() -> new CommandSpec("", "", new Object()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullHandlerIsRejected() {
            assertThatThrownBy(() -> new CommandSpec("cmd", "", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullSpecIsRejected() {
            registration.openWindow();

            assertThatThrownBy(() -> registration.register(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void registrationOrderIsPreserved() {
            registration.openWindow();
            registration.register(new CommandSpec("a", "", new Object()));
            registration.register(new CommandSpec("b", "", new Object()));

            assertThat(registration.registeredCommands())
                    .extracting(CommandSpec::name)
                    .containsExactly("a", "b");
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesCommandRegistration() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getCommandRegistration()).isNotNull();
        }

        @Test
        void runtimeShutdownClearsCommands() {
            AprismRuntime runtime = AprismRuntime.instance();
            CommandRegistrationImpl impl = (CommandRegistrationImpl) runtime.getCommandRegistration();
            impl.openWindow();
            impl.register(new CommandSpec("cmd", "", new Object()));

            runtime.shutdown();

            CommandRegistrationImpl fresh = (CommandRegistrationImpl) runtime.getCommandRegistration();
            assertThat(fresh.isWindowOpen()).isFalse();
            assertThat(fresh.registeredCommands()).isEmpty();
        }
    }
}
