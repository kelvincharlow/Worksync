package com.eclectics.worksync.users.bootstrap;

import java.io.Console;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class FirstAdminBootstrapGuardTests {
    @Test
    void noExplicitActionMeansNoContextOrInput() {
        Console console = mock(Console.class);
        assertThat(FirstAdminBootstrap.run(new String[0], console, Map.of())).isEqualTo(2);
        verifyNoInteractions(console);
    }

    @Test
    void extraArgumentsCannotSupplyCredentials() {
        Console console = mock(Console.class);
        assertThat(FirstAdminBootstrap.run(new String[]{"--create-first-admin", "unexpected"}, console, Map.of()))
                .isEqualTo(2);
        verifyNoInteractions(console);
    }

    @Test
    void nonInteractiveExecutionIsRefused() {
        assertThat(FirstAdminBootstrap.run(new String[]{"--create-first-admin"}, null, Map.of())).isEqualTo(2);
    }

    @Test
    void missingExplicitDatabaseEnvironmentIsRefused() {
        Console console = mock(Console.class);
        assertThat(FirstAdminBootstrap.run(new String[]{"--create-first-admin"}, console, Map.of())).isEqualTo(2);
        verify(console, never()).readPassword(anyString());
    }

    @Test
    void cancellationDoesNotStartAContext() {
        Console console = mock(Console.class);
        when(console.readLine("Type CREATE FIRST ADMIN to continue: ")).thenReturn("cancel");
        assertThat(FirstAdminBootstrap.run(new String[]{"--create-first-admin"}, console,
                Map.of("DB_URL", "not-a-real-url", "POSTGRES_USER", "fixture", "POSTGRES_PASSWORD", "fixture")))
                .isEqualTo(2);
        verify(console, never()).readPassword(anyString());
    }

    @Test
    void mismatchedPasswordsAreErasedWithoutLoggingThem(CapturedOutput output) {
        Console console = mock(Console.class);
        when(console.readLine("Type CREATE FIRST ADMIN to continue: ")).thenReturn("CREATE FIRST ADMIN");
        when(console.readPassword("First name: ")).thenReturn("First".toCharArray());
        when(console.readPassword("Last name: ")).thenReturn("Last".toCharArray());
        when(console.readPassword("Email: ")).thenReturn("fixture@example.invalid".toCharArray());
        when(console.readPassword("Phone (E.164): ")).thenReturn("+254712345678".toCharArray());
        char[] password = "operator-input-fixture-one".toCharArray();
        char[] confirmation = "operator-input-fixture-two".toCharArray();
        when(console.readPassword("Password (12+ characters, maximum 72 UTF-8 bytes): ")).thenReturn(password);
        when(console.readPassword("Confirm password: ")).thenReturn(confirmation);
        assertThat(FirstAdminBootstrap.run(new String[]{"--create-first-admin"}, console,
                Map.of("DB_URL", "not-a-real-url", "POSTGRES_USER", "fixture", "POSTGRES_PASSWORD", "fixture")))
                .isEqualTo(2);
        assertThat(password).containsOnly('\0');
        assertThat(confirmation).containsOnly('\0');
        assertThat(output.getAll().contains("operator-input-fixture")).isFalse();
    }
}
