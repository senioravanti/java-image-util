package ru.senioravanti.imgconv;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

import java.util.List;

import ru.senioravanti.imgconv.handlers.ImageConverterSubcommand;
import ru.senioravanti.imgconv.handlers.PdfSubcommand;
import ru.senioravanti.imgconv.handlers.QRCodeSubcommandHandler;
import ru.senioravanti.imgconv.handlers.SubcommandHandler;

public class App {
    private static final List<SubcommandHandler> HANDLERS = List.of(
        new QRCodeSubcommandHandler(),
        new ImageConverterSubcommand(),
        new PdfSubcommand()
    );

    private static CommandSpec build() {
        var s = CommandSpec.create();
        s.mixinStandardHelpOptions(true);
        for (var h : HANDLERS) {
            var sc = h.register();
            s.addSubcommand(h.getName(), sc);
        }
        return s;
    }

    static int run(ParseResult pr) {
        var helpExitCode = CommandLine.executeHelpRequest(pr);
        if (helpExitCode != null) {
            return helpExitCode;
        }
        var sc = pr.subcommand();
        if (sc == null) {
            return 1;
        }
        return HANDLERS.stream()
            .filter(it -> it.getName().equals(sc.commandSpec().name()))
            .findFirst()
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
