package ru.senioravanti.imgconv.model;

import java.io.InputStream;

import ru.senioravanti.commons.result.CheckedSupplier;
import ru.senioravanti.imgconv.utils.FileUtils;

public record Entry(
    String name,
    String contentType,
    CheckedSupplier<InputStream> content
) {
    public static Entry of(String name, CheckedSupplier<InputStream> content) {
        var contentType = FileUtils.guessContentType(content);
        return new Entry(name, contentType, content);
    }
}
