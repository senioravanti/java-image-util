package ru.senioravanti.imgconv.utils;

import java.nio.file.Path;

public class FileUtils {
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
}
