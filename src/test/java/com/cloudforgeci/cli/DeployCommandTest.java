package com.cloudforgeci.cli;

import com.cloudforge.core.local.DeploymentTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeployCommandTest {

    @Test
    void parsesContextAndTarget() {
        DeployCommand.Arguments args = DeployCommand.Arguments.parse(
            new String[] {"--context", "deployment-context.json", "--target", "ministack"});

        assertEquals(Path.of("deployment-context.json"), args.contextFile());
        assertEquals(DeploymentTarget.MINISTACK, args.target());
    }

    @Test
    void missingContextIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> DeployCommand.Arguments.parse(new String[] {"--target", "ministack"}));
    }

    @Test
    void awsTargetIsRejected() {
        // Real AWS self-deploys go through Manager's own DirectDeployService, not this tool --
        // see DeployCommand's own class javadoc for why.
        assertThrows(IllegalArgumentException.class,
            () -> DeployCommand.Arguments.parse(new String[] {"--context", "x.json", "--target", "aws"}));
    }

    @Test
    void omittedTargetDefaultsToAwsAndIsThenRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> DeployCommand.Arguments.parse(new String[] {"--context", "x.json"}));
    }

    @Test
    void unknownFlagIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> DeployCommand.Arguments.parse(new String[] {"--bogus", "value"}));
    }

    @Test
    void flagMissingItsValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> DeployCommand.Arguments.parse(new String[] {"--context"}));
    }
}
