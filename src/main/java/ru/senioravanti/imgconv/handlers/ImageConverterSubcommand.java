package ru.senioravanti.imgconv.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

import javax.imageio.ImageIO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import ru.senioravanti.commons.loggers.CustomMapMessage;
import ru.senioravanti.imgconv.utils.FileUtils;

public class ImageConverterSubcommand extends BaseSubcommandHandler {
    private static final Logger LOGGER = LogManager.getLogger(ImageConverterSubcommand.class);
    private static final String NAME = "conv";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public CommandSpec register() {
        var s = super.register();
        s.addPositional(PositionalParamSpec.builder()
            .index("0")
            .paramLabel("FILE")
            .description("Input file")
            .required(true)
            .type(Path.class)
            .build());
        s.addPositional(PositionalParamSpec.builder()
            .index("1")
            .paramLabel("FILE")
            .description("Output file")
            .type(Path.class)
            .required(true)
            .build());
        return s;
    }

    @Override
    public Integer apply(ParseResult pr) {
        var helpExitCode = CommandLine.executeHelpRequest(pr);
        if (helpExitCode != null) {
            return helpExitCode;
        }
        Path inputPath = pr.matchedPositionalValue(0, null);
        Path outputPath = pr.matchedPositionalValue(1, null);
        var formatName = FileUtils.getExtension(outputPath);
        try {
            var image = ImageIO.read(inputPath.toFile());
            if (!ImageIO.write(image, formatName, outputPath.toFile())) {
                System.err.printf("failed to convert image into format `%s`\n", formatName);
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
}
