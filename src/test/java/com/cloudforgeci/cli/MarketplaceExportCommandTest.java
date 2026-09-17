package com.cloudforgeci.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketplaceExportCommandTest {

    @Test
    void parsesContextAndOutput() {
        MarketplaceExportCommand.Arguments args = MarketplaceExportCommand.Arguments.parse(
            new String[] {"--context", "deployment-context.json", "--output", "template.json"});

        assertEquals(Path.of("deployment-context.json"), args.contextFile());
        assertEquals(Path.of("template.json"), args.output());
    }

    @Test
    void outputIsOptional() {
        MarketplaceExportCommand.Arguments args = MarketplaceExportCommand.Arguments.parse(
            new String[] {"--context", "deployment-context.json"});

        assertNull(args.output());
    }

    @Test
    void missingContextIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> MarketplaceExportCommand.Arguments.parse(new String[] {"--output", "template.json"}));
    }

    @Test
    void unknownFlagIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> MarketplaceExportCommand.Arguments.parse(new String[] {"--bogus", "value"}));
    }

    @Test
    void flagMissingItsValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> MarketplaceExportCommand.Arguments.parse(new String[] {"--context"}));
    }
}
