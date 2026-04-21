# Service 包

## 概述
本包提供了应用级别的服务类，封装业务逻辑和外部服务调用。

## 核心类

### SpeechRecognitionService
语音识别服务，提供语音录制和语音转文字功能。

**Spring 注解：** `@Service`

**依赖：**
- `whisper-jni`: OpenAI Whisper 的 Java JNI 绑定
- `opencc4j`: 中文繁简转换库

**功能：**
- 录制音频（16kHz, 16bit, 单声道）
- 使用本地 Whisper 模型进行语音识别
- 支持中文语音识别
- 自动将繁体中文转换为简体中文

**核心方法：**
- `startRecording()`: 开始录音
- `stopRecordingAndTranscribe()`: 停止录音并进行语音识别，返回CompletableFuture
- `isRecording()`: 检查是否正在录音
- `isInitialized()`: 检查 Whisper 是否已初始化
- `getModelStatus()`: 获取模型状态信息
- `cancel()`: 取消录音
- `cleanup()`: 释放 Whisper 资源

**音频格式：**
- 采样率：16000 Hz
- 采样位数：16 bit
- 声道数：1（单声道）
- 格式：PCM

**模型配置：**
- 模型文件：`ggml-medium.bin`（使用 medium 模型，识别准确率更高）
- 模型路径：`~/.autiva/models/ggml-medium.bin`（使用 AppConstants.Base.APP_DIR）
- 模型下载：https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin
- 模型大小：约 1.5 GB

**使用示例：**
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final SpeechRecognitionService speechRecognitionService;
    
    public void recordAndTranscribe() {
        if (!speechRecognitionService.isInitialized()) {
            System.out.println(speechRecognitionService.getModelStatus());
            return;
        }
        
        speechRecognitionService.startRecording();
        
        speechRecognitionService.stopRecordingAndTranscribe()
            .thenAccept(text -> {
                if (StringUtils.isNotBlank(text)) {
                    System.out.println("识别结果: " + text);
                }
            });
    }
}
```

**录音状态：**
- 使用 AtomicBoolean 保证线程安全
- 提供 `recording` 属性供UI绑定

**初始化流程：**
1. 加载 Whisper JNI 本地库
2. 检查模型文件是否存在
3. 加载模型到内存
4. 初始化完成，可以进行语音识别

**注意事项：**
- 首次使用需要手动下载模型文件
- 模型文件需放置在 `~/.autiva/models/` 目录
- 需要麦克风权限
- 录音在独立线程中进行，避免阻塞UI线程
- 语音识别在异步线程中执行

## 设计模式
- 服务层模式：封装业务逻辑
- 异步处理：使用 CompletableFuture 进行异步语音识别
- 单例模式：WhisperContext 全局唯一

## 注意事项
1. 需要麦克风权限
2. 录音在独立线程中进行，避免阻塞UI线程
3. 语音识别需要加载本地模型，首次使用需下载
4. 模型文件约 1.5GB（medium 模型），需要足够的磁盘空间
5. 语音识别过程会占用一定内存（约 2GB）
6. 识别结果自动从繁体转换为简体中文
