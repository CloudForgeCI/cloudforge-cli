package com.cloudforgeci.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers only the fast-fail argument-validation paths (no {@code docker} binary needed) --
 * start/stop/restart/status against a real emulator container is exercised manually (see
 * scripts/assemble-release.sh's own verification note), same as cfc-testing's own emulator goals
 * are never unit-tested against a live Docker daemon.
 */
class EmulatorCommandTest {

    @Test
    void missingActionIsRejected() {
        assertEquals(1, EmulatorCommand.run(new String[] {}));
    }

    @Test
    void unknownActionIsRejected() {
        assertEquals(1, EmulatorCommand.run(new String[] {"bogus", "--target", "ministack"}));
    }

    @Test
    void startWithoutTargetIsRejected() {
        assertEquals(1, EmulatorCommand.run(new String[] {"start"}));
    }

    @Test
    void awsTargetIsRejected() {
        assertEquals(1, EmulatorCommand.run(new String[] {"start", "--target", "aws"}));
    }

    @Test
    void statusWithoutTargetReportsBothEmulatorsAsOneJsonLineEach() {
        // Both adapters are real dependencies of this module (see pom.xml), so
        // LocalEmulatorRuntimes.forTarget resolves either way; running/healthy come back false
        // when Docker/the container itself isn't available rather than throwing (every layer down
        // to DockerEmulatorSupport catches that itself) -- this only checks the JSON shape, not
        // any particular running/healthy value.
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured));
        int exitCode;
        try {
            exitCode = EmulatorCommand.run(new String[] {"status"});
        } finally {
            System.setOut(original);
        }
        assertEquals(0, exitCode);
        String output = captured.toString();
        assertTrue(output.contains("\"target\":\"ministack\""));
        assertTrue(output.contains("\"target\":\"localstack\""));
    }
}
