package com.cloudforgeci.cli;

import com.cloudforge.core.config.ApplicationPropertyLoader;
import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforgeci.api.core.iam.ManagerOperatorIamSupport;
import com.cloudforgeci.api.deploy.CloudForgeSynthesizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * {@code cloudforge-cli marketplace-export-template} — synthesizes the static CloudFormation
 * template AWS Marketplace hosts for its "AWS CloudFormation" product type listing. Synth-only,
 * deliberately: a Marketplace listing template is launched by AWS's own console from a fixed
 * copy this command produces once at publish time, never {@code cdk deploy}'d live the way
 * {@link DeployCommand}'s local-emulator bootstrap or Manager's own self-deploy are.
 *
 * <p>The supplied context file's {@code marketplaceDeploymentEnabled} flag is baked into the
 * exported template as-is (see {@link ManagerOperatorIamSupport#marketplaceEntitlementStatement}).
 * The actual product code cloudforge-manager checks at runtime is never sourced from here, or
 * from any deploy-time config at all — it's a constant compiled into cloudforge-manager itself
 * (see {@code MarketplaceConfiguration.PRODUCT_CODE}'s javadoc for why). This command's job is
 * only to confirm the flag that turns the check on is actually set, not to carry a product
 * code.</p>
 */
final class MarketplaceExportCommand {

    private MarketplaceExportCommand() {
    }

    static int run(String[] args) {
        Path contextFile;
        Path output;
        try {
            Arguments parsed = Arguments.parse(args);
            contextFile = parsed.contextFile();
            output = parsed.output();
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

        if (!ManagerOperatorIamSupport.APPLICATION_ID.equals(config.applicationId)) {
            Json.emit("load", "error", Map.of("message",
                "applicationId must be " + ManagerOperatorIamSupport.APPLICATION_ID
                    + " — AWS Marketplace's CloudFormation product type is a cloudforge-manager-only "
                    + "listing, not a per-application one."));
            return 1;
        }
        if (!Boolean.TRUE.equals(config.marketplaceDeploymentEnabled)) {
            Json.emit("load", "error", Map.of("message",
                "marketplaceDeploymentEnabled is not set in " + contextFile + " — set it to "
                    + "true before exporting, so the template that ships to customers starts "
                    + "the entitlement check. The product code it checks is compiled into "
                    + "cloudforge-manager itself, not sourced from this context file."));
            return 1;
        }

        Json.emit("synth", "started", Map.of("stackName", String.valueOf(config.stackName)));
        CloudForgeSynthesizer.Result synthesis;
        try {
            Path outputDirectory = Files.createTempDirectory("cfc-marketplace-export-");
            synthesis = CloudForgeSynthesizer.synthesize(config, outputDirectory);
        } catch (IOException | RuntimeException e) {
            Json.emit("synth", "error", Map.of("message", String.valueOf(e.getMessage())));
            return 2;
        }

        try {
            Path destination = resolveDestination(output, synthesis.templateFile());
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.copy(synthesis.templateFile(), destination, StandardCopyOption.REPLACE_EXISTING);
            Json.emit("synth", "complete", Map.of(
                "stackName", synthesis.stackName(),
                "templateFile", destination.toString()));
            return 0;
        } catch (IOException e) {
            Json.emit("synth", "error", Map.of("message",
                "Synthesized but could not copy the template to its destination: " + e.getMessage()));
            return 2;
        }
    }

    /** Default destination is {@code <stack-name>.template.json} in the current directory — a
     *  directory passed as {@code --output} keeps that same filename inside it, matching how most
     *  CLI export flags treat a trailing-slash-shaped destination. */
    private static Path resolveDestination(Path output, Path templateFile) {
        if (output == null) {
            return Path.of(templateFile.getFileName().toString());
        }
        if (Files.isDirectory(output)) {
            return output.resolve(templateFile.getFileName());
        }
        return output;
    }

    record Arguments(Path contextFile, Path output) {
        static Arguments parse(String[] args) {
            String context = null;
            String outputArg = null;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--context" -> {
                        requireValue(args, i, "--context");
                        context = args[++i];
                    }
                    case "--output" -> {
                        requireValue(args, i, "--output");
                        outputArg = args[++i];
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            if (context == null || context.isBlank()) {
                throw new IllegalArgumentException(
                    "Usage: cloudforge-cli marketplace-export-template --context "
                        + "<deployment-context.json> [--output <file-or-directory>]");
            }
            return new Arguments(Path.of(context), outputArg == null ? null : Path.of(outputArg));
        }

        private static void requireValue(String[] args, int index, String flag) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
        }
    }
}
