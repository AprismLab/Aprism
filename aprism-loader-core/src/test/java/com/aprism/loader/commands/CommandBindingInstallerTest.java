package com.aprism.loader.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.commands.CommandSpec;

/**
 * JUnit 5 + AssertJ tests for {@link CommandBindingInstaller} (v26.5-Alpha.4).
 *
 * @author BlockConnect@StarsailsClover
 */
class CommandBindingInstallerTest {

    private CommandRegistrationImpl registration;
    private CommandBindingInstaller installer;

    @BeforeEach
    void setUp() {
        registration = new CommandRegistrationImpl();
        registration.openWindow();
        installer = new CommandBindingInstaller(registration);
    }

    /**
     * A recording bridge that tracks bind/unbind calls for assertions.
     */
    private static class RecordingBridge implements CommandDispatcherBridge {
        final List<CommandSpec> bound = new ArrayList<>();
        boolean unbindAllCalled;

        @Override
        public void bind(CommandSpec spec) {
            bound.add(spec);
        }

        @Override
        public void unbindAll() {
            unbindAllCalled = true;
        }
    }

    /**
     * A bridge that throws on a specific command name.
     */
    private static class ThrowingBridge implements CommandDispatcherBridge {
        final String failName;

        ThrowingBridge(String failName) {
            this.failName = failName;
        }

        @Override
        public void bind(CommandSpec spec) {
            if (failName.equals(spec.name())) {
                throw new RuntimeException("simulated bind failure for " + spec.name());
            }
        }

        @Override
        public void unbindAll() {
        }
    }

    @Nested
    class BridgeAttachment {

        @Test
        void noBridgeByDefault() {
            assertThat(installer.isBridgeAttached()).isFalse();
        }

        @Test
        void setBridgeAttaches() {
            installer.setBridge(new RecordingBridge());
            assertThat(installer.isBridgeAttached()).isTrue();
        }

        @Test
        void setNullDetaches() {
            installer.setBridge(new RecordingBridge());
            installer.setBridge(null);
            assertThat(installer.isBridgeAttached()).isFalse();
        }
    }

    @Nested
    class BindCommands {

        @Test
        void bindCommandsWithoutBridgeIsNoOp() {
            registration.register(new CommandSpec("test", "desc", new Object()));
            registration.freezeWindow();

            int bound = installer.bindCommands();
            assertThat(bound).isEqualTo(0);
        }

        @Test
        void bindAllCommandsWithBridge() {
            var bridge = new RecordingBridge();
            installer.setBridge(bridge);

            registration.register(new CommandSpec("cmd1", "desc1", new Object()));
            registration.register(new CommandSpec("cmd2", "desc2", new Object()));
            registration.freezeWindow();

            int bound = installer.bindCommands();
            assertThat(bound).isEqualTo(2);
            assertThat(bridge.bound).hasSize(2);
            assertThat(bridge.bound.get(0).name()).isEqualTo("cmd1");
            assertThat(bridge.bound.get(1).name()).isEqualTo("cmd2");
        }

        @Test
        void bindingEmptyCommandListReturnsZero() {
            registration.freezeWindow();
            installer.setBridge(new RecordingBridge());

            int bound = installer.bindCommands();
            assertThat(bound).isEqualTo(0);
        }

        @Test
        void failingCommandDoesNotBlockOthers() {
            var bridge = new ThrowingBridge("bad");
            installer.setBridge(bridge);

            registration.register(new CommandSpec("good1", "d", new Object()));
            registration.register(new CommandSpec("bad", "d", new Object()));
            registration.register(new CommandSpec("good2", "d", new Object()));
            registration.freezeWindow();

            int bound = installer.bindCommands();
            assertThat(bound).isEqualTo(2);
        }

        @Test
        void bindCommandsCanBeCalledMultipleTimes() {
            var bridge = new RecordingBridge();
            installer.setBridge(bridge);

            registration.register(new CommandSpec("cmd1", "d", new Object()));
            registration.freezeWindow();

            installer.bindCommands();
            installer.bindCommands();

            // The bridge receives each command twice (idempotent from the
            // installer's perspective; the bridge deduplicates if needed).
            assertThat(bridge.bound).hasSize(2);
        }
    }

    @Nested
    class Unbind {

        @Test
        void unbindAllCallsBridgeUnbindAll() {
            var bridge = new RecordingBridge();
            installer.setBridge(bridge);

            installer.unbindAll();

            assertThat(bridge.unbindAllCalled).isTrue();
            assertThat(installer.isBridgeAttached()).isFalse();
        }

        @Test
        void unbindAllWithoutBridgeIsNoOp() {
            installer.unbindAll();
            assertThat(installer.isBridgeAttached()).isFalse();
        }

        @Test
        void unbindAllCatchesThrowingBridge() {
            installer.setBridge(new CommandDispatcherBridge() {
                @Override
                public void bind(CommandSpec spec) {
                }

                @Override
                public void unbindAll() {
                    throw new RuntimeException("simulated unbind failure");
                }
            });

            // Should not throw
            installer.unbindAll();
            assertThat(installer.isBridgeAttached()).isFalse();
        }
    }

    @Nested
    class ConstructorValidation {

        @Test
        void nullRegistrationThrows() {
            try {
                new CommandBindingInstaller(null);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}
