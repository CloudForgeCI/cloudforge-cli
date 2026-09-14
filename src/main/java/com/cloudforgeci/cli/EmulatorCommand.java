package com.cloudforgeci.cli;

import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.local.EmulatorLifecycle;
import com.cloudforge.core.local.EmulatorLifecycleAction;
import com.cloudforge.core.local.LocalEmulatorRuntime;
import com.cloudforge.core.local.LocalEmulatorRuntimes;
import com.cloudforge.core.local.StackPortRuntime;
import com.cloudforge.core.local.StackPortRuntimes;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code cloudforge-cli emulator} — start/stop/restart/status a local MiniStack or LocalStack
 * emulator (Docker container + its StackPort/nginx-edge companions), the same lifecycle
 * {@code cfc-testing}'s {@code --platform} flag and the Maven emulator goals already drive,
 * exposed as a standalone command so CloudForge Studio can shell out to it without a full
 * cfc-core checkout.
 *
 * <p>{@link EmulatorLifecycle#execute} itself never writes to stdout for START/STOP/RESTART (only
 * its STATUS case does, in human-readable form) — status here is built directly from each
 * runtime's own accessors instead, so every line this command prints stays valid JSON.</p>
 */
final class EmulatorCommand {

    private EmulatorCommand() {
    }

    static int run(String[] args) {
        if (args.length == 0) {
            Json.emit("usage", "error", Map.of("message", usage()));
            return 1;
        }
        String action = args[0];
        String targetArg = null;
        for (int i = 1; i < args.length; i++) {
            if ("--target".equals(args[i])) {
                if (i + 1 >= args.length) {
                    Json.emit("usage", "error", Map.of("message", "--target requires a value"));
                    return 1;
                }
                targetArg = args[++i];
            } else {
                Json.emit("usage", "error", Map.of("message", "Unknown argument: " + args[i]));
                return 1;
            }
        }

        if (!"status".equals(action) && (targetArg == null || targetArg.isBlank())) {
            Json.emit("usage", "error", Map.of("message", "--target <ministack|localstack> is required for start/stop/restart"));
            return 1;
        }

        DeploymentTarget target;
        try {
            target = targetArg == null ? null : requireTarget(targetArg);
        } catch (IllegalArgumentException e) {
            Json.emit("usage", "error", Map.of("message", e.getMessage()));
            return 1;
        }

        return switch (action) {
            case "start" -> lifecycle(EmulatorLifecycleAction.START, target);
            case "stop" -> lifecycle(EmulatorLifecycleAction.STOP, target);
            case "restart" -> lifecycle(EmulatorLifecycleAction.RESTART, target);
            case "status" -> status(targetArg);
            default -> {
                Json.emit("usage", "error", Map.of("message", "Unknown emulator action: " + action + "\n" + usage()));
                yield 1;
            }
        };
    }

    private static int lifecycle(EmulatorLifecycleAction action, DeploymentTarget target) {
        String verb = action.name().toLowerCase(Locale.ROOT);
        Json.emit("emulator", verb + "ing", Map.of("target", target.configKey()));
        try {
            EmulatorLifecycle.execute(target, action);
        } catch (IOException | RuntimeException e) {
            Json.emit("emulator", "error", Map.of("target", target.configKey(), "message", String.valueOf(e.getMessage())));
            return 2;
        }
        Json.emit("emulator", verb + "ed", statusFields(target));
        return 0;
    }

    private static int status(String targetArg) {
        List<DeploymentTarget> targets;
        try {
            targets = targetArg == null
                ? List.of(DeploymentTarget.MINISTACK, DeploymentTarget.LOCALSTACK)
                : List.of(requireTarget(targetArg));
        } catch (IllegalArgumentException e) {
            Json.emit("usage", "error", Map.of("message", e.getMessage()));
            return 1;
        }
        for (DeploymentTarget target : targets) {
            try {
                Json.emit("emulator", "status", statusFields(target));
            } catch (RuntimeException e) {
                Json.emit("emulator", "error", Map.of("target", target.configKey(), "message", String.valueOf(e.getMessage())));
                return 2;
            }
        }
        return 0;
    }

    /** Emulator container + StackPort companion status — the two things worth showing a user
     *  deciding whether "Deploy CloudForge Manager Here" is ready to run. */
    private static Map<String, Object> statusFields(DeploymentTarget target) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("target", target.configKey());
        try {
            LocalEmulatorRuntime runtime = LocalEmulatorRuntimes.forTarget(target);
            fields.put("containerName", runtime.containerName());
            fields.put("running", safeRunning(runtime));
            fields.put("healthy", runtime.isHealthy());
            fields.put("healthEndpoint", String.valueOf(runtime.healthEndpoint()));
        } catch (RuntimeException e) {
            fields.put("running", false);
            fields.put("healthy", false);
            fields.put("message", String.valueOf(e.getMessage()));
        }
        try {
            StackPortRuntime stackPort = StackPortRuntimes.forTarget(target);
            fields.put("stackPortRunning", safeRunning(stackPort));
            fields.put("stackPortUrl", String.valueOf(stackPort.browserUrl()));
        } catch (RuntimeException ignored) {
            // StackPort is an optional companion -- absence isn't reported as an error.
        }
        return fields;
    }

    private static boolean safeRunning(LocalEmulatorRuntime runtime) {
        try {
            return runtime.isRunning();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean safeRunning(StackPortRuntime runtime) {
        try {
            return runtime.isRunning();
        } catch (IOException e) {
            return false;
        }
    }

    private static DeploymentTarget requireTarget(String targetArg) {
        DeploymentTarget target = DeploymentTarget.fromConfigKey(targetArg);
        if (target == DeploymentTarget.AWS) {
            throw new IllegalArgumentException(
                "--target aws is not supported here — this manages local MiniStack/LocalStack "
                    + "emulator containers only.");
        }
        return target;
    }

    private static String usage() {
        return "Usage: cloudforge-cli emulator <start|stop|restart|status> --target <ministack|localstack>\n"
            + "       cloudforge-cli emulator status  (no --target: reports both)";
    }
}
