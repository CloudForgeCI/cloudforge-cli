package com.cloudforgeci.cli;

import java.util.Arrays;

/**
 * Dispatches to this tool's two subcommands: {@code deploy} (synth+deploy an application,
 * {@link DeployCommand}) and {@code emulator} (start/stop/restart/status a local MiniStack/
 * LocalStack emulator, {@link EmulatorCommand}). Both speak the same JSON-lines-on-stdout
 * protocol, so a caller streaming this process's output parses one line format regardless of
 * which subcommand ran.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            Json.emit("usage", "error", java.util.Map.of("message", usage()));
            System.exit(1);
        }
        String subcommand = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        int exitCode = switch (subcommand) {
            case "deploy" -> DeployCommand.run(rest);
            case "emulator" -> EmulatorCommand.run(rest);
            default -> {
                Json.emit("usage", "error", java.util.Map.of("message", "Unknown subcommand: " + subcommand + "\n" + usage()));
                yield 1;
            }
        };
        System.exit(exitCode);
    }

    private static String usage() {
        return "Usage:\n"
            + "  cloudforge-cli deploy --context <deployment-context.json> --target <ministack|localstack>\n"
            + "  cloudforge-cli emulator <start|stop|restart|status> --target <ministack|localstack>\n"
            + "  cloudforge-cli emulator status  (no --target: reports both)";
    }
}
