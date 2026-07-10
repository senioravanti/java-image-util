package ru.senioravanti.imgconv.handlers;

import lombok.Getter;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

import javax.imageio.ImageIO;

import java.awt.Color;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Files;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import ru.senioravanti.commons.loggers.CustomMapMessage;

public class QRCodeSubcommandHandler extends BaseSubcommandHandler {
    private static final Logger LOGGER = LogManager.getLogger(QRCodeSubcommandHandler.class);
    private static final String NAME = "qr";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public CommandSpec register() {
        var s = super.register();
        s.addPositional(PositionalParamSpec.builder()
            .index("0")
            .paramLabel("URL")
            .description("URL to be encoded")
            .required(true)
            .type(String.class)
            .build());
        s.addPositional(PositionalParamSpec.builder()
            .index("1")
            .paramLabel("FILE")
            .description("Output file")
            .required(true)
            .type(Path.class)
            .build());
        s.addOption(OptionSpec.builder("--size", "-S")
            .paramLabel("SIZE")
            .description("QR code size")
            .defaultValue("400")
            .required(false)
            .type(Integer.class)
            .build());
        s.addOption(OptionSpec.builder("--logo", "-L")
            .paramLabel("IMAGE")
            .description("Brand logotype")
            .required(false)
            .type(Path.class)
            .build());
        return s;
    }

    private BufferedImage readLogo(Path path, int size) throws IOException, TranscoderException {
        if (!Files.probeContentType(path).equals("image/svg+xml")) {
            var source = ImageIO.read(path.toFile());
            if (source.getWidth() == size && source.getHeight() == size) return source;
            var scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, size, size, null);
            g.dispose();
            return scaled;
        }
        var transcoder = new ImageTranscoder() {
            @Getter
            private BufferedImage image;

            @Override
            public BufferedImage createImage(int width, int height) {
                image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                return image;
            }

            @Override
            public void writeImage(BufferedImage image, TranscoderOutput transcoderOutput) {
                this.image = image;
            }
        };
        transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) size);
        transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (float) size);
        try (var in = Files.newInputStream(path)) {
            transcoder.transcode(new TranscoderInput(in), null);
        }
        return transcoder.getImage();
    }

    @Override
    public Integer apply(ParseResult pr) {
        String url = pr.matchedPositionalValue(0, null);
        Path outputPath = pr.matchedPositionalValue(1, null);
        Integer size = pr.matchedOptionValue("--size", 400);
        Path logoPath = pr.matchedOptionValue("--logo", null);

        var hints = new HashMap<EncodeHintType, Object>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            var matrix = new MultiFormatWriter()
                .encode(url, BarcodeFormat.QR_CODE, size, size, hints);
            var qrImage = MatrixToImageWriter.toBufferedImage(matrix);
            if (logoPath != null) {
                int logoSize = size / 5;
                int padding = Math.max(4, logoSize / 12);
                int backgroundSize = logoSize + padding * 2;
                int arc = backgroundSize / 3;
                int logoX = (size - logoSize) / 2;
                int logoY = (size - logoSize) / 2;
                int backgroundX = logoX - padding;
                int backgroundY = logoY - padding;

                var combined = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

                var g = combined.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(qrImage, 0, 0, null);

                g.setColor(Color.WHITE);
                g.fill(new RoundRectangle2D.Double(backgroundX, backgroundY, backgroundSize, backgroundSize, arc, arc));

                var oldClip = g.getClip();
                g.setClip(new RoundRectangle2D.Double(logoX, logoY, logoSize, logoSize, arc, arc));
                g.drawImage(readLogo(logoPath, logoSize), logoX, logoY, null);

                g.setClip(oldClip);
                g.dispose();

                qrImage = combined;
            }
            if (!ImageIO.write(qrImage, "png", outputPath.toFile())) {
                LOGGER.error(CustomMapMessage.of("failed to write qrcode", Map.of("path", outputPath)));
                System.err.printf("cannot write qrcode to `%s`\n", outputPath);
                return 1;
            }
            return 0;
        } catch (WriterException ex) {
            LOGGER.error(CustomMapMessage.of("failed to encode", Map.of("url", url), ex));
            System.err.printf("cannot encode url `%s`\n", url);
            return 1;
        } catch (IOException ex) {
            var params = new HashMap<String, Object>();
            params.put("logo", logoPath);
            LOGGER.error(CustomMapMessage.of("failed to read", params, ex));
            System.err.printf("cannot read logo `%s`\n", logoPath);
            return 1;
        } catch (TranscoderException ex) {
            var params = new HashMap<String, Object>();
            params.put("logo", logoPath);
            LOGGER.error(CustomMapMessage.of("failed to transcode svg", params, ex));
            System.err.printf("cannot transcode svg logo `%s`\n", logoPath);
            return 1;
        }
    }
}
