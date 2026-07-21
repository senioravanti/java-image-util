package ru.senioravanti.imgconv.handlers;

import static picocli.CommandLine.Model.CommandSpec;

import static ru.senioravanti.imgconv.App.PROPS;

public abstract class BaseSubcommandHandler implements SubcommandHandler {
    @Override
    public CommandSpec register() {
        var s = CommandSpec.create();
        s.mixinStandardHelpOptions(true);
        s.version(PROPS.version());
        return s;
    }
}
