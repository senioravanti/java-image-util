package ru.senioravanti.imgconv.utils;

import java.nio.file.Files;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.Tika;

import java.io.InputStream;
import java.nio.file.Path;

import ru.senioravanti.commons.loggers.CustomMapMessage;
import ru.senioravanti.commons.result.CheckedSupplier;
import ru.senioravanti.commons.result.Result;

public class FileUtils {
    private static final Logger LOGGER = LogManager.getLogger(FileUtils.class);
    private static final Tika TIKA = new Tika();

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public static String getExtension(Path path) {
        var fileName = path.getFileName();
        if (fileName == null) return "";
        String fileString = fileName.toString();

        // Find index of last dot; return empty string if not found or is
        // at first or last position
        int lastDotIndex = fileString.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == fileString.length() - 1) return "";

        // Return characters after, but not including, last dot
        return fileString.substring(lastDotIndex + 1);
    }

    public static String guessContentType(byte[] content) {
        return Result
            .from(() -> TIKA.detect(content))
            .handle(Exception.class, ex -> LOGGER.warn(CustomMapMessage.of("failed to detect content type", ex)))
            .orElse(DEFAULT_CONTENT_TYPE);
    }

    public static String guessContentType(CheckedSupplier<InputStream> supplier) {
        try (var inputStream = supplier.get()) {
            var buf = new byte[8 * 1024];
            inputStream.read(buf);
            return guessContentType(buf);
        } catch (Exception ex) {
            throw new RuntimeException("failed to read first 8 kilobytes", ex);
        }
    }

    public static String guessContentType(Path path) {
        return Result
            .from(() -> guessContentType(() -> Files.newInputStream(path)))
            .handle(RuntimeException.class, ex -> LOGGER.error(CustomMapMessage.of("failed to detect content type", Map.of("path", path), ex)))
            .orElse(DEFAULT_CONTENT_TYPE);
    }
}
