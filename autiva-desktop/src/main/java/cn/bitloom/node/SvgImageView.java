package cn.bitloom.node;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.Getter;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Getter
public class SvgImageView extends ImageView {

    private String svgPath;
    private boolean loaded = false;

    public SvgImageView() {
        super();
        fitWidthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0 && svgPath != null && !loaded) {
                loadSvg();
            }
        });
        fitHeightProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0 && svgPath != null && !loaded) {
                loadSvg();
            }
        });
    }

    public SvgImageView(String svgPath) {
        this();
        this.svgPath = svgPath;
    }

    public void setSvgPath(String svgPath) {
        this.svgPath = svgPath;
        this.loaded = false;
        if (getFitWidth() > 0 && getFitHeight() > 0) {
            loadSvg();
        }
    }

    private void loadSvg() {
        if (svgPath == null || svgPath.isEmpty() || loaded) {
            return;
        }
        if (getFitWidth() <= 0 || getFitHeight() <= 0) {
            return;
        }
        loaded = true;
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

            Image fxImage = new Image(new ByteArrayInputStream(baos.toByteArray()));
            setImage(fxImage);
        } catch (Exception e) {
            System.err.println("Failed to load SVG: " + svgPath + ", " + e.getMessage());
        }
    }
}
