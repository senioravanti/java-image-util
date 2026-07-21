package ru.senioravanti.imgconv;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

import ru.senioravanti.imgconv.model.ConfigurationProperties;
import ru.senioravanti.imgconv.utils.ConfigurationLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

import ru.senioravanti.imgconv.handlers.ImageConverterSubcommand;
import ru.senioravanti.imgconv.handlers.PdfSubcommand;
import ru.senioravanti.imgconv.handlers.QRCodeSubcommandHandler;
import ru.senioravanti.imgconv.handlers.SubcommandHandler;

public class App {
    private static final Logger LOGGER = LogManager.getLogger(App.class);
    private static final Map<String, SubcommandHandler> HANDLERS = Map.of(
        "qr", new QRCodeSubcommandHandler(),
        "conv", new ImageConverterSubcommand(),
        "pdf", new PdfSubcommand()
    );

    public static final JsonMapper JSON = new JsonMapper();
    public static final ConfigurationProperties PROPS = ConfigurationLoader.loadConfig("config.yaml");

    private static CommandSpec build() {
        var s = CommandSpec.create();
        s.mixinStandardHelpOptions(true);
        HANDLERS.forEach((name, handler) -> s.addSubcommand(name, handler.register()));
        return s;
    }

    static int run(ParseResult pr) {
        var helpExitCode = CommandLine.executeHelpRequest(pr);
        if (helpExitCode != null) return helpExitCode;
        var sc = pr.subcommand();
        if (sc == null) return 1;
        return Optional
            .ofNullable(HANDLERS.get(sc.commandSpec().name()))
            .map(it -> it.apply(sc))
            .orElseGet(() -> {
                System.err.println("unknown subcommand");
                return 1;
            });
    }

    static void main(String... argv) {
        var cli = new CommandLine(build());
        cli.setExecutionStrategy(App::run);
        int exitCode = cli.execute(argv);
        System.exit(exitCode);
    }
}
