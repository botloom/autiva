package cn.bitloom.pet;

import cn.bitloom.agentic.session.SessionManager;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 萌宠状态管理器，负责加载/保存萌宠状态、监听消息更新、定期分析聊天风格。
 */
@Slf4j
@Component
public class PetStateManager {

    private static final String PET_DIR = System.getProperty("user.home") + "/.autiva/pet";
    private static final String STATE_FILE = PET_DIR + "/state.json";
    private static final int STYLE_ANALYZE_INTERVAL = 10;

    private final ChatStyleAnalyzer analyzer = new ChatStyleAnalyzer();
    private final SessionManager sessionManager;

    @Getter
    private PetState state;
    private int lastAnalyzedCount = 0;

    public PetStateManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @PostConstruct
    public void init() {
        loadState();
    }

    /**
     * 通知有新消息追加，更新萌宠状态。
     *
     * @param messageCount 新增的消息数量
     */
    public void onMessagesAdded(int messageCount) {
        state.setTotalMessages(state.getTotalMessages() + messageCount);
        updateGrowthProgress();

        // 定期重新分析聊天风格
        if (state.getTotalMessages() - lastAnalyzedCount >= STYLE_ANALYZE_INTERVAL) {
            analyzeAndSetPetType();
            lastAnalyzedCount = state.getTotalMessages();
        }

        saveState();
    }

    /**
     * 强制重新分析聊天风格。
     */
    public void forceAnalyze() {
        analyzeAndSetPetType();
        saveState();
    }

    /**
     * 重置萌宠状态。
     */
    public void reset() {
        state = new PetState();
        lastAnalyzedCount = 0;
        saveState();
    }

    /**
     * 保存窗口位置。
     */
    public void savePosition(double x, double y) {
        state.setPosX(x);
        state.setPosY(y);
        saveState();
    }

    private void updateGrowthProgress() {
        GrowthStage stage = state.getGrowthStage();
        double progress = stage.getProgress(state.getTotalMessages());
        // 全局进度：基于阶段和阶段内进度计算 0~1
        int stageIndex = stage.ordinal();
        int totalStages = GrowthStage.values().length;
        state.setGrowthProgress((stageIndex + progress) / totalStages);
    }

    private void analyzeAndSetPetType() {
        try {
            // 获取所有用户会话的消息
            var allSessions = sessionManager.getAllUserSessions();
            List<Message> allMessages = new java.util.ArrayList<>();
            for (var session : allSessions) {
                allMessages.addAll(session.getMessages());
            }

            if (allMessages.isEmpty()) return;

            ChatStyleAnalyzer.StyleScores scores = analyzer.getDimensionScores(
                    allMessages.stream()
                            .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage)
                            .map(Message::getText)
                            .filter(t -> t != null && !t.isBlank())
                            .toList()
            );

            state.setAvgLength(scores.avgLength());
            state.setCodeRatio(scores.codeRatio());
            state.setEmojiRate(scores.emojiRate());
            state.setFrequency(scores.frequency());
            state.setDiversity(scores.diversity());

            PetType newType = analyzer.analyze(allMessages);
            if (newType != state.getPetType()) {
                log.info("萌宠植物类型变化: {} → {}", state.getPetType(), newType);
                state.setPetType(newType);
            }
        } catch (Exception e) {
            log.warn("聊天风格分析失败", e);
        }
    }

    private void loadState() {
        try {
            Path path = Path.of(STATE_FILE);
            if (Files.exists(path)) {
                String json = Files.readString(path);
                state = JSON.parseObject(json, PetState.class);
                if (state.getPetType() == null) {
                    state.setPetType(PetType.SUNFLOWER);
                }
                lastAnalyzedCount = state.getTotalMessages();
                log.info("萌宠状态加载成功: type={}, messages={}", state.getPetType(), state.getTotalMessages());
                return;
            }
        } catch (Exception e) {
            log.warn("萌宠状态加载失败，使用默认状态", e);
        }
        state = new PetState();
    }

    private void saveState() {
        try {
            Path dir = Path.of(PET_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String json = JSON.toJSONString(state, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
            Files.writeString(Path.of(STATE_FILE), json);
        } catch (Exception e) {
            log.warn("萌宠状态保存失败", e);
        }
    }
}
