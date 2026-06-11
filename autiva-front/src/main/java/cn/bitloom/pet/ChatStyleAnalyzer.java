package cn.bitloom.pet;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 聊天风格分析器，基于5个维度分析用户聊天风格并映射到植物类型。
 * <p>
 * 维度：
 * - avgLength: 平均消息长度（短0 → 长1）
 * - codeRatio: 代码比例（无代码0 → 大量代码1）
 * - emojiRate: emoji使用率（无emoji0 → 大量emoji1）
 * - frequency: 消息频率（低频0 → 高频1）
 * - diversity: 词汇多样性（重复0 → 多样1）
 */
public class ChatStyleAnalyzer {

    private static final Pattern CODE_PATTERN = Pattern.compile("(```[\\s\\S]*?```|`[^`]+`|\\bdef\\b|\\bclass\\b|\\bfunction\\b|\\bimport\\b|\\breturn\\b|\\bvar\\b|\\bconst\\b|\\blet\\b|\\bif\\b|\\bfor\\b|\\bwhile\\b)");
    private static final Pattern EMOJI_PATTERN = Pattern.compile("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F1E0}-\\x{1F1FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[！？!?,，。…~～]+");
    private static final Set<String> EMOTION_WORDS = Set.of(
            "开心", "高兴", "难过", "伤心", "喜欢", "讨厌", "爱", "恨", "感动", "幸福",
            "焦虑", "担心", "害怕", "惊喜", "失望", "愤怒", "无聊", "兴奋", "满足", "感恩",
            "哈哈", "嘻嘻", "呜呜", "唉", "啊", "哇"
    );

    /**
     * 风格维度得分
     */
    public record StyleScores(double avgLength, double codeRatio, double emojiRate,
                              double frequency, double diversity) {
    }

    /**
     * 分析用户消息列表，返回最匹配的植物类型。
     */
    public PetType analyze(List<Message> messages) {
        List<String> userTexts = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(Message::getText)
                .filter(Objects::nonNull)
                .filter(t -> !t.isBlank())
                .toList();

        if (userTexts.isEmpty()) {
            return PetType.SUNFLOWER;
        }

        StyleScores scores = getDimensionScores(userTexts);
        return mapToPetType(scores);
    }

    /**
     * 计算5个维度的得分（0~1）。
     */
    public StyleScores getDimensionScores(List<String> userTexts) {
        if (userTexts == null || userTexts.isEmpty()) {
            return new StyleScores(0.5, 0, 0, 0.5, 0.5);
        }

        double avgLength = computeAvgLength(userTexts);
        double codeRatio = computeCodeRatio(userTexts);
        double emojiRate = computeEmojiRate(userTexts);
        double frequency = computeFrequency(userTexts);
        double diversity = computeDiversity(userTexts);

        return new StyleScores(avgLength, codeRatio, emojiRate, frequency, diversity);
    }

    private double computeAvgLength(List<String> texts) {
        double avg = texts.stream().mapToInt(String::length).average().orElse(0);
        // 归一化：0字→0, 20字→0.3, 50字→0.6, 100字→0.8, 200字→1.0
        return Math.min(1.0, avg / 200.0);
    }

    private double computeCodeRatio(List<String> texts) {
        long codeChars = 0;
        long totalChars = 0;
        for (String text : texts) {
            totalChars += text.length();
            var matcher = CODE_PATTERN.matcher(text);
            while (matcher.find()) {
                codeChars += matcher.group().length();
            }
        }
        if (totalChars == 0) return 0;
        return Math.min(1.0, (double) codeChars / totalChars * 3);
    }

    private double computeEmojiRate(List<String> texts) {
        long emojiCount = 0;
        long totalChars = 0;
        for (String text : texts) {
            totalChars += text.length();
            var matcher = EMOJI_PATTERN.matcher(text);
            while (matcher.find()) {
                emojiCount++;
            }
        }
        if (totalChars == 0) return 0;
        return Math.min(1.0, (double) emojiCount / Math.max(1, texts.size()) * 2);
    }

    private double computeFrequency(List<String> texts) {
        // 基于消息数量估算频率，10条以下低频，50条以上高频
        double count = texts.size();
        return Math.min(1.0, count / 50.0);
    }

    private double computeDiversity(List<String> texts) {
        Set<String> uniqueWords = new HashSet<>();
        int totalWords = 0;
        for (String text : texts) {
            String[] words = text.split("[\\s,，。.!！?？;；:：、]+");
            for (String word : words) {
                if (word.length() > 1) {
                    uniqueWords.add(word.toLowerCase());
                    totalWords++;
                }
            }
        }
        if (totalWords == 0) return 0.5;
        return Math.min(1.0, (double) uniqueWords.size() / totalWords * 3);
    }

    /**
     * 将风格维度得分映射到植物类型。
     * 使用加权评分：每个 PetType 有预设的维度偏好权重，计算综合得分，选最高的。
     */
    private PetType mapToPetType(StyleScores s) {
        // 每种植物的偏好权重 [avgLength, codeRatio, emojiRate, frequency, diversity]
        double[][] weights = {
                /* SUNFLOWER */ {0.3, -0.5, 0.8, 0.5, 0.2},
                /* CACTUS    */ {-0.4, 0.9, -0.3, 0.1, 0.1},
                /* IVY       */ {0.7, -0.2, 0.1, 0.2, 0.8},
                /* BAMBOO    */ {-0.3, 0.1, 0.1, 0.9, 0.3},
                /* ROSE      */ {0.4, -0.3, 0.5, 0.3, 0.6},
                /* BONSAI    */ {0.8, -0.1, -0.2, -0.5, 0.5}
        };

        double[] scores = {s.avgLength, s.codeRatio, s.emojiRate, s.frequency, s.diversity};

        PetType bestType = PetType.SUNFLOWER;
        double bestScore = Double.NEGATIVE_INFINITY;

        PetType[] types = PetType.values();
        for (int i = 0; i < types.length; i++) {
            double totalScore = 0;
            for (int j = 0; j < 5; j++) {
                totalScore += weights[i][j] * scores[j];
            }
            // 加上情感词额外加分（对 ROSE）
            if (types[i] == PetType.ROSE) {
                totalScore += s.emojiRate * 0.2;
            }
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestType = types[i];
            }
        }

        return bestType;
    }
}
