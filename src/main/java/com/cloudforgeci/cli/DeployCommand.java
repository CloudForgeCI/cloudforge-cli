package com.cloudforgeci.cli;

import com.cloudforge.core.config.ApplicationPropertyLoader;
import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforgeci.api.deploy.CloudForgeDeployment;
import com.cloudforgeci.api.deploy.CloudForgeSynthesizer;
import com.cloudforgeci.api.deploy.DeploymentRequest;
import com.cloudforgeci.api.deploy.DeploymentResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code cloudforge-cli deploy} — non-interactive synth+deploy, CloudForge Studio's bundled
 * counterpart to {@code InteractiveDeployer}'s own MiniStack/LocalStack deploy path (options
 * 6/7/8), stripped of every interactive menu since Studio drives this from a schema-collected
 * deployment-context file instead of a terminal prompt.
 *
 * <p>Deliberately real-AWS-target-less: {@code --target aws} is refused outright. Real AWS
 * self-deploys already have their own dedicated path (Manager's own {@code DirectDeployService}),
 * and refusing here keeps this tool's blast radius — and its entitlement/licensing surface —
 * confined to the local-emulator bootstrap it exists for.</p>
 */
final class DeployCommand {

    private DeployCommand() {
    }

    static int run(String[] args) {
        Path contextFile;
        DeploymentTarget target;
        try {
            Arguments parsed = Arguments.parse(args);
            contextFile = parsed.contextFile();
            target = parsed.target();
        } catch (IllegalArgumentException e) {
            Json.emit("usage", "error", Map.of("message", e.getMessage()));
            return 1;
        }

        DeploymentConfig config;
        try {
            config = DeploymentConfig.fromFile(contextFile);
            ApplicationPropertyLoader.applyPropertyDefaults(config);
        } catch (IOException e) {
            Json.emit("load", "error", Map.of("message", "Could not read " + contextFile + ": " + e.getMessage()));
            return 1;
        }

        Json.emit("synth", "started", Map.of("stackName", String.valueOf(config.stackName)));
        CloudForgeSynthesizer.Result synthesis;
        try {
            Path outputDirectory = Files.createTempDirectory("cfc-deploy-cli-");
            synthesis = CloudForgeSynthesizer.synthesize(config, outputDirectory);
        } catch (IOException | RuntimeException e) {
            Json.emit("synth", "error", Map.of("message", String.valueOf(e.getMessage())));
            return 2;
        }
        Json.emit("synth", "complete", Map.of("stackName", synthesis.stackName()));

        Json.emit("deploy", "started", Map.of("stackName", synthesis.stackName()));
        DeploymentResult result;
        try {
            DeploymentRequest request = DeploymentRequest.deploy(
                config, target, synthesis.templateFile(), synthesis.assemblyDirectory());
            result = CloudForgeDeployment.deploy(request);
        } catch (IOException | RuntimeException e) {
            Json.emit("deploy", "error", Map.of("message", String.valueOf(e.getMessage())));
            return 3;
        }

        if (!result.success()) {
            Json.emit("deploy", "error", Map.of(
                "message", "Deploy reported failure",
                "messages", result.messages()));
            return 3;
        }

        Map<String, Object> complete = new LinkedHashMap<>();
        complete.put("stackName", result.localStackName());
        complete.put("endpoint", result.endpoint());
        complete.put("outputs", result.outputs());
        complete.put("messages", result.messages());
        result.warning().ifPresent(w -> complete.put("warning", w));
        Json.emit("deploy", "complete", complete);
        return 0;
    }

    record Arguments(Path contextFile, DeploymentTarget target) {
        static Arguments parse(String[] args) {
            String context = null;
            String targetArg = null;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--context" -> {
                        requireValue(args, i, "--context");
                        context = args[++i];
                    }
                    case "--target" -> {
                        requireValue(args, i, "--target");
                        targetArg = args[++i];
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            if (context == null || context.isBlank()) {
                throw new IllegalArgumentException(
                    "Usage: cloudforge-cli deploy --context <deployment-context.json> --target <ministack|localstack>");
            }
            DeploymentTarget target = DeploymentTarget.fromConfigKey(targetArg);
            if (target == DeploymentTarget.AWS) {
                throw new IllegalArgumentException(
                    "--target aws is not supported here — this tool is for local-emulator "
                        + "bootstrap only, real AWS self-deploys go through Manager's own "
                        + "deploy:create path.");
            }
            return new Arguments(Path.of(context), target);
        }

        private static void requireValue(String[] args, int index, String flag) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
        }
    }
}
