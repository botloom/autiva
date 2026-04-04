package cn.bitloom.node;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import lombok.Getter;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Getter
public class SvgImageView extends ImageView {

    private String svgPath;

    public SvgImageView() {
        super();
    }

    public SvgImageView(String svgPath) {
        super();
        this.svgPath = svgPath;
        loadSvg();
    }

    public void setSvgPath(String svgPath) {
        this.svgPath = svgPath;
        loadSvg();
    }

    private void loadSvg() {
        if (svgPath == null || svgPath.isEmpty()) {
            return;
        }
        try (InputStream inputStream = SvgImageView.class.getResourceAsStream(svgPath)) {
            if (inputStream == null) {
                System.err.println("Resource not found: " + svgPath);
                return;
            }
            byte[] svgBytes = inputStream.readAllBytes();

            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) getFitWidth());
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) getFitHeight());

            ByteArrayInputStream bais = new ByteArrayInputStream(svgBytes);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            transcoder.transcode(new TranscoderInput(bais), new TranscoderOutput(baos));

            BufferedImage bufferedImage = javax.imageio.ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
            setImage(SwingFXUtils.toFXImage(bufferedImage, null));
        } catch (Exception e) {
            System.err.println("Failed to load SVG: " + svgPath + ", " + e.getMessage());
        }
    }
}
