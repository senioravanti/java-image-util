package ru.senioravanti.imgconv.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

import javax.imageio.ImageIO;

import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.zip.ZipFile;

import ru.senioravanti.commons.loggers.CustomMapMessage;
import ru.senioravanti.commons.result.Result;
import ru.senioravanti.imgconv.utils.FileUtils;

public class PdfSubcommand extends BaseSubcommandHandler {
    private static final Logger LOGGER = LogManager.getLogger(PdfSubcommand.class);
    private static final String NAME = "pdf";
    private static final List<String> ZIP_CONTENT_TYPES = List.of("application/zip", "application/x-zip-compressed");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public CommandSpec register() {
        var s = super.register();
        s.addPositional(PositionalParamSpec.builder()
            .index("0")
            .paramLabel("ZIP")
            .description("Zip archive containing images to merge")
            .required(true)
            .type(Path.class)
            .build());
        s.addPositional(PositionalParamSpec.builder()
            .index("1")
            .paramLabel("PDF")
            .description("Output pdf file")
            .required(true)
            .type(Path.class)
            .build());
        return s;
    }

    @Override
    public Integer apply(ParseResult pr) {
        Path zipPath = pr.matchedPositionalValue(0, null);
        Path pdfPath = pr.matchedPositionalValue(1, null);

        var zipArchiveContentType = FileUtils.guessContentType(zipPath);
        if (!ZIP_CONTENT_TYPES.contains(zipArchiveContentType)) {
            LOGGER.error(CustomMapMessage.of("invalid content type", Map.of("path", zipPath, "contentType", zipArchiveContentType)));
            System.err.println("The first positional argument must be a valid ZIP archive");
            return 1;
        }

        try (var pdfDocument = new PDDocument()) {
            try (var zipFile = new ZipFile(zipPath.toFile())) {
                zipFile.stream()
                    .filter(it -> !it.isDirectory())
                    .filter(it -> Result
                        .from(() -> FileUtils.guessContentType(() -> zipFile.getInputStream(it)).startsWith("image/"))
                        .handle(RuntimeException.class, ex -> LOGGER.error(CustomMapMessage.of("failed to probe", Map.of("name", it.getName()), ex)))
                        .orElse(false)
                    )
                    .forEach(it -> {
                        try (var is = zipFile.getInputStream(it)) {
                            var image = ImageIO.read(is);
                            if (image == null) throw new RuntimeException("failed to decode content of image `%s`".formatted(it.getName()));
                            var page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
                            pdfDocument.addPage(page);
                            var pdfImage = LosslessFactory.createFromImage(pdfDocument, image);
                            try (var cs = new PDPageContentStream(pdfDocument, page)) {
                                cs.drawImage(pdfImage, 0, 0, image.getWidth(), image.getHeight());
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException("failed to read image `%s`".formatted(it.getName()), ex);
                        }
                    });
            } catch (IOException ex) {
                LOGGER.error(CustomMapMessage.of("failed to list archive", Map.of("path", zipPath), ex));
                System.err.println("Can't list archive");
                return 1;
            } catch (RuntimeException ex) {
                System.err.println("Can't merge images into pdf");
                LOGGER.error(CustomMapMessage.of("failed to merge images into pdf", ex));
                return 1;
            }
            pdfDocument.save(pdfPath.toFile());
            return 0;
        } catch (IOException ex) {
            LOGGER.error(CustomMapMessage.of("failed to create pdf document", ex));
            System.err.println("Can't create pdf document");
            return 1;
        }
    }
}
