package ru.senioravanti.imgconv.handlers;

import static picocli.CommandLine.Model.CommandSpec;

public abstract class BaseSubcommandHandler implements SubcommandHandler {
    @Override
    public CommandSpec register() {
        var s = CommandSpec.create();
        s.mixinStandardHelpOptions(true);
        return s;
    }
}
