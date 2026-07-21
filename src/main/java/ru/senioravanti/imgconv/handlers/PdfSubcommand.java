package ru.senioravanti.imgconv.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

import javax.imageio.ImageIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import ru.senioravanti.commons.loggers.CustomMapMessage;
import ru.senioravanti.imgconv.model.Entry;
import ru.senioravanti.imgconv.utils.FileUtils;

public class PdfSubcommand extends BaseSubcommandHandler {
    private static final Logger LOGGER = LogManager.getLogger(PdfSubcommand.class);
    private static final List<String> ZIP_CONTENT_TYPES = List.of("application/zip", "application/x-zip-compressed");
    private static final Map<String, SubcommandHandler> HANDLERS = Map.of(
        "rotate", new RotateSubcommandHandler(),
        "merge", new MergeSubcommandHandler()
    );

    static class RotateSubcommandHandler extends BaseSubcommandHandler {
        private void rotate(PDPage page, int angle) {
            int normalized = Math.floorMod(angle, 360);
            page.setRotation(normalized);
        }

        @Override
        public CommandSpec register() {
            var s = super.register();
            s.addOption(OptionSpec.builder("--angle", "-A")
                .paramLabel("ANGLE")
                .description("Angles divisible by 90 degrees")
                .defaultValue("90")
                .type(Short.class)
                .build());
            s.addOption(OptionSpec.builder("--page", "-P")
                .paramLabel("PAGE")
                .description("Page number (1-based), omit it for all pages")
                .type(Integer.class)
                .build());
            s.addPositional(PositionalParamSpec.builder()
                .index("0")
                .paramLabel("IN")
                .description("Pdf file to rotate")
                .required(true)
                .type(Path.class)
                .build());
            s.addPositional(PositionalParamSpec.builder()
                .index("1")
                .paramLabel("OUT")
                .description("Output pdf file")
                .required(true)
                .type(Path.class)
                .build());
            return s;
        }

        @Override
        public Integer apply(ParseResult pr) {
            var sc = pr.subcommand();
            Short angle = sc.matchedOptionValue("--angle", (short) 90);
            Integer pageNumber = sc.matchedOptionValue("--page", null);
            Path inPath = sc.matchedPositionalValue(0, null);
            Path outPath = sc.matchedPositionalValue(1, null);
            if (angle % 90 != 0) {
                System.err.println("Only angles divisible by 90 are supported");
                return 1;
            }
            try (var pdfDoc = Loader.loadPDF(inPath.toFile())) {
                System.out.printf("Pdf `%s` successfully rotated `%s` by %d degree\n", inPath, outPath, angle);
                if (pageNumber != null) {
                    int idx = pageNumber - 1;
                    if (idx < 0 || idx >= pdfDoc.getNumberOfPages()) {
                        System.err.printf("Page %d is out of range (document has %d pages)\n", pageNumber, pdfDoc.getNumberOfPages());
                        return 1;
                    }
                    rotate(pdfDoc.getPage(idx), angle);
                } else {
                    pdfDoc.getPages().forEach(it -> rotate(it, angle));
                }
                pdfDoc.save(outPath.toFile());
                return 0;
            } catch (IOException ex) {
                LOGGER.error(CustomMapMessage.of("failed to rotate pdf", ex));
                System.err.println("Can't rotate pdf");
                return 1;
            }
        }

    }

    static class MergeSubcommandHandler extends BaseSubcommandHandler {
        private final PDFMergerUtility merger = new PDFMergerUtility();

        public interface EntrySource extends AutoCloseable {
            Stream<Entry> stream() throws IOException;

            @Override
            default void close() throws IOException {
            }
        }

        private void addPage(PDDocument pdfDoc, Entry entry) {
            try (var is = entry.content().get()) {
                if (entry.contentType().equals("application/pdf")) {
                    try (var pdfEntry = Loader.loadPDF(RandomAccessReadBuffer.createBufferFromStream(is))) {
                        merger.appendDocument(pdfDoc, pdfEntry);
                    }
                    return;
                }
                var image = ImageIO.read(is);
                if (image == null) throw new RuntimeException("failed to decode content of image `%s`".formatted(entry.name()));
                var page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
                var pdfImage = JPEGFactory.createFromImage(pdfDoc, image, .85f);
                try (var cs = new PDPageContentStream(pdfDoc, page)) {
                    cs.drawImage(pdfImage, 0, 0, image.getWidth(), image.getHeight());
                }
                pdfDoc.addPage(page);
            } catch (Exception ex) {
                throw new RuntimeException("failed to read image `%s`".formatted(entry.name()), ex);
            }
        }

        private EntrySource getDirectoryEntries(Path inPath) throws IOException {
            return () -> Files.walk(inPath)
                .filter(Files::isRegularFile)
                .map(path -> Entry.of(
                    path.getFileName().toString(),
                    () -> Files.newInputStream(path)
                ));
        }

        private EntrySource getZipEntries(Path inPath) throws IOException {
            var zipFile = new ZipFile(inPath.toFile());
            return new EntrySource() {
                @Override
                public Stream<Entry> stream() {
                    return zipFile.stream()
                        .map(it -> Entry.of(
                            it.getName(),
                            () -> zipFile.getInputStream(it)
                        ));
                }

                @Override
                public void close() throws IOException {
                    zipFile.close();
                }
            };
        }

        @Override
        public CommandSpec register() {
            var s = super.register();
            s.addPositional(PositionalParamSpec.builder()
                .index("0")
                .paramLabel("IN")
                .description("Directory or zip archive containing images or pdfs to merge")
                .required(true)
                .type(Path.class)
                .build());
            s.addPositional(PositionalParamSpec.builder()
                .index("1")
                .paramLabel("OUT")
                .description("Output pdf file")
                .required(true)
                .type(Path.class)
                .build());
            return s;
        }


        @Override
        public Integer apply(ParseResult pr) {
            var sc = pr.subcommand();
            Path inPath = sc.matchedPositionalValue(0, null);
            Path outPath = sc.matchedPositionalValue(1, null);
            try (var pdfDoc = new PDDocument()) {
                try {
                    var contentType = FileUtils.guessContentType(inPath);
                    EntrySource entrySource;
                    if (Files.isDirectory(inPath)) {
                        entrySource = getDirectoryEntries(inPath);
                    } else if (ZIP_CONTENT_TYPES.contains(contentType)) {
                        entrySource = getZipEntries(inPath);
                    } else {
                        LOGGER.error(CustomMapMessage.of("invalid content type", Map.of("path", inPath, "contentType", contentType)));
                        System.err.println("The first positional argument must be directory or a valid ZIP archive");
                        return 1;
                    }
                    try (entrySource) {
                        entrySource.stream()
                            .filter(it -> it.contentType().startsWith("image/")
                                || it.contentType().equals("application/pdf"))
                            .sorted(Comparator.comparing(Entry::name))
                            .forEach(it -> addPage(pdfDoc, it));
                    }
                } catch (IOException ex) {
                    LOGGER.error(CustomMapMessage.of("failed to list archive", Map.of("path", inPath), ex));
                    System.err.println("Can't list archive");
                    return 1;
                } catch (RuntimeException ex) {
                    System.err.println("Can't merge images into pdf");
                    LOGGER.error(CustomMapMessage.of("failed to merge images into pdf", ex));
                    return 1;
                }
                pdfDoc.save(outPath.toFile());
                System.out.printf("Merged pdf `%s` successfully created\n", outPath);
                return 0;
            } catch (
                IOException ex) {
                LOGGER.error(CustomMapMessage.of("failed to create pdf document", ex));
                System.err.println("Can't create pdf document");
                return 1;
            }
        }
    }

    @Override
    public CommandSpec register() {
        var s = super.register();
        HANDLERS.forEach((name, handler) -> s.addSubcommand(name, handler.register()));
        return s;
    }

    @Override
    public Integer apply(ParseResult pr) {
        return Optional
            .ofNullable(pr.subcommand())
            .map(it -> HANDLERS.get(it.commandSpec().name()))
            .map(it -> it.apply(pr))
            .orElseGet(() -> {
                System.err.println("unknown subcommand");
                return 1;
            });
    }
}
