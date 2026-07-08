package ru.senioravanti.imgconv;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

import javax.imageio.ImageIO;

import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;

import ru.senioravanti.commons.loggers.CustomMapMessage;
import ru.senioravanti.imgconv.model.Format;

public class App {

    private static final Logger LOGGER = LogManager.getLogger(App.class);

    private static CommandSpec build() {
        var s = CommandSpec.create();
        s.mixinStandardHelpOptions(true);
        s.addPositional(PositionalParamSpec.builder()
            .index("0")
            .paramLabel("FILE")
            .description("Input file")
            .required(true)
            .type(Path.class)
            .build());
        s.addOption(OptionSpec.builder("--out", "-o")
            .paramLabel("FILE")
            .description("Output file")
            .type(Path.class)
            .required(true)
            .build());
        s.addOption(OptionSpec.builder("--format", "-f")
            .paramLabel("FORMAT")
            .description("Output format")
            .required(true)
            .type(Format.class)
            .build());
        return s;
    }

    static int run(ParseResult pr) {
        var helpExitCode = CommandLine.executeHelpRequest(pr);
        if (helpExitCode != null) {
            return helpExitCode;
        }

        Path inputPath = pr.matchedPositionalValue(0, null);
        Path outputPath = pr.matchedOptionValue("--out", null);
        Format format = pr.matchedOptionValue("--format", null);

        try {
            var image = ImageIO.read(inputPath.toFile());
            if (!ImageIO.write(image, format.name(), outputPath.toFile())) {
                System.err.printf("failed to convert image into format `%s`\n", format.name());
                return 1;
            }
            System.out.printf("image `%s` successfully converted to `%s`\n", inputPath, outputPath);
            return 0;
        } catch (IOException ex) {
            LOGGER.error(
                CustomMapMessage.of("failed to read image", Map.of("path", inputPath), ex));
            return 1;
        }
    }

    static void main(String... argv) {
        var cli = new CommandLine(build());
        cli.setExecutionStrategy(App::run);
        int exitCode = cli.execute(argv);
        System.exit(exitCode);
    }
}
