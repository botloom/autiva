package cn.bitloom.service;

import cn.bitloom.constant.AppConstants;
import cn.bitloom.store.Store;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import javafx.application.Platform;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SpeechRecognitionService {

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000, 16, 1, true, false);
    private static final String MODEL_FILENAME = "ggml-medium.bin";
    private static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin";
    
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private TargetDataLine microphone;
    private ByteArrayOutputStream audioBuffer;
    
    @Getter
    private final AtomicBoolean recording = new AtomicBoolean(false);
    
    private WhisperJNI whisperJNI;
    private WhisperContext whisperContext;
    private boolean initialized = false;

    public SpeechRecognitionService() {
        initWhisper();
    }

    private synchronized void initWhisper() {
        if (initialized) {
            return;
        }
        try {
            WhisperJNI.loadLibrary();
            whisperJNI = new WhisperJNI();
            
            Path modelPath = getModelPath();
            if (!Files.exists(modelPath)) {
                log.info("Whisper model not found at: {}", modelPath);
                log.info("Please download the model from: {}", MODEL_URL);
                Platform.runLater(() -> Store.statusText.set("请下载Whisper模型"));
                return;
            }
            
            whisperContext = whisperJNI.init(modelPath);
            if (whisperContext == null) {
                log.error("Failed to initialize Whisper context");
                return;
            }
            
            initialized = true;
            log.info("Whisper initialized successfully with model: {}", modelPath);
        } catch (IOException e) {
            log.error("Failed to load Whisper library", e);
        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load native library for Whisper", e);
        }
    }

    private Path getModelPath() {
        return AppConstants.Base.APP_DIR.resolve("models").resolve(MODEL_FILENAME);
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public String getModelStatus() {
        if (initialized) {
            return "Whisper模型已加载";
        }
        Path modelPath = getModelPath();
        if (!Files.exists(modelPath)) {
            return "请下载模型到: " + modelPath;
        }
        return "Whisper初始化失败";
    }

    public void startRecording() {
        if (isRecording.get()) {
            log.warn("Already recording");
            return;
        }

        if (!initialized) {
            log.error("Whisper not initialized");
            Platform.runLater(() -> Store.statusText.set(getModelStatus()));
            return;
        }

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                log.error("Audio format not supported");
                Platform.runLater(() -> Store.statusText.set("音频格式不支持"));
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(AUDIO_FORMAT);
            microphone.start();

            audioBuffer = new ByteArrayOutputStream();
            isRecording.set(true);
            recording.set(true);

            Platform.runLater(() -> Store.statusText.set("正在录音..."));

            Thread recordingThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording.get()) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioBuffer.write(buffer, 0, bytesRead);
                    }
                }
            }, "AudioRecordingThread");
            recordingThread.setDaemon(true);
            recordingThread.start();

            log.info("Recording started");
        } catch (LineUnavailableException e) {
            log.error("Failed to start recording", e);
            Platform.runLater(() -> Store.statusText.set("无法启动录音"));
        }
    }

    public CompletableFuture<String> stopRecordingAndTranscribe() {
        if (!isRecording.get()) {
            return CompletableFuture.completedFuture("");
        }

        isRecording.set(false);
        recording.set(false);

        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }

        Platform.runLater(() -> Store.statusText.set("正在识别语音..."));

        byte[] audioData = audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];
        
        if (audioData.length == 0) {
            Platform.runLater(() -> Store.statusText.set("未录制到音频"));
            return CompletableFuture.completedFuture("");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                float[] samples = convertToFloatArray(audioData);
                String result = transcribe(samples);
                Platform.runLater(() -> Store.statusText.set("就绪"));
                return result;
            } catch (Exception e) {
                log.error("Failed to transcribe audio", e);
                Platform.runLater(() -> Store.statusText.set("语音识别失败: " + e.getMessage()));
                return "";
            }
        });
    }

    private float[] convertToFloatArray(byte[] audioData) {
        int sampleCount = audioData.length / 2;
        float[] samples = new float[sampleCount];
        
        for (int i = 0; i < sampleCount; i++) {
            int low = audioData[i * 2] & 0xFF;
            int high = audioData[i * 2 + 1];
            short sample = (short) ((high << 8) | low);
            samples[i] = sample / 32768.0f;
        }
        
        return samples;
    }

    private String transcribe(float[] samples) {
        if (!initialized || whisperContext == null) {
            throw new RuntimeException("Whisper not initialized");
        }

        WhisperFullParams params = new WhisperFullParams();
        params.language = "zh";
        params.translate = false;

        int result = whisperJNI.full(whisperContext, params, samples, samples.length);
        if (result != 0) {
            throw new RuntimeException("Transcription failed with code: " + result);
        }

        int numSegments = whisperJNI.fullNSegments(whisperContext);
        StringBuilder transcript = new StringBuilder();
        
        for (int i = 0; i < numSegments; i++) {
            String text = whisperJNI.fullGetSegmentText(whisperContext, i);
            if (text != null && !text.trim().isEmpty()) {
                transcript.append(text.trim()).append(" ");
            }
        }

        String rawResult = transcript.toString().trim();
        return convertToSimplifiedChinese(rawResult);
    }

    private String convertToSimplifiedChinese(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            return ZhConverterUtil.toSimple(text);
        } catch (Exception e) {
            log.warn("Failed to convert to simplified Chinese, returning original text", e);
            return text;
        }
    }

    public void cancel() {
        if (isRecording.get()) {
            isRecording.set(false);
            recording.set(false);
            if (microphone != null) {
                microphone.stop();
                microphone.close();
            }
            Platform.runLater(() -> Store.statusText.set("就绪"));
        }
    }

    public void cleanup() {
        if (whisperContext != null) {
            whisperJNI.free(whisperContext);
            whisperContext = null;
        }
        initialized = false;
    }
}
