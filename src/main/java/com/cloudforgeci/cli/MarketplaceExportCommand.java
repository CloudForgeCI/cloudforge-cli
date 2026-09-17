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
 * <p>The supplied context file's {@code marketplaceProductCode} is baked into the exported
 * template as-is (see {@link ManagerOperatorIamSupport#marketplaceEntitlementStatement}) — a
 * Marketplace listing has exactly one product code decided at listing-creation time, so this
 * command doesn't invent or validate one, it just requires the context file already carry the
 * real one before export.</p>
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
        if (config.marketplaceProductCode == null || config.marketplaceProductCode.isBlank()) {
            Json.emit("load", "error", Map.of("message",
                "marketplaceProductCode is not set in " + contextFile + " — set it to this "
                    + "listing's real AWS Marketplace product code before exporting, so the "
                    + "template that ships to customers already carries it."));
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
                "templateFile", destination.toString(),
                "productCode", config.marketplaceProductCode));
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
