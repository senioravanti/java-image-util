package ru.senioravanti.imgconv.handlers;

import static picocli.CommandLine.Model.CommandSpec;
import static picocli.CommandLine.ParseResult;

import java.util.function.Function;

public interface SubcommandHandler extends Function<ParseResult, Integer> {
    String getName();
    CommandSpec register();
}
