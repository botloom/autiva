package cn.bitloom.pet;

/**
 * 生长阶段枚举，根据消息数量映射不同的生长阶段。
 */
public enum GrowthStage {

    SEED("种子", 0, 5),
    SPROUT("萌芽", 5, 20),
    SEEDLING("幼苗", 20, 50),
    YOUNG("少年", 50, 100),
    MATURE("成熟", 100, Integer.MAX_VALUE);

    private final String label;
    private final int minMessages;
    private final int maxMessages;

    GrowthStage(String label, int minMessages, int maxMessages) {
        this.label = label;
        this.minMessages = minMessages;
        this.maxMessages = maxMessages;
    }

    public String getLabel() {
        return label;
    }

    public int getMinMessages() {
        return minMessages;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * 根据消息数量获取对应的生长阶段。
     */
    public static GrowthStage fromMessageCount(int count) {
        for (GrowthStage stage : values()) {
            if (count >= stage.minMessages && count < stage.maxMessages) {
                return stage;
            }
        }
        return MATURE;
    }

    /**
     * 计算在当前阶段内的进度（0.0~1.0）。
     */
    public double getProgress(int messageCount) {
        if (messageCount <= minMessages) return 0.0;
        if (messageCount >= maxMessages) return 1.0;
        int range = maxMessages - minMessages;
        if (range <= 0) return 1.0;
        return (double) (messageCount - minMessages) / range;
    }
}
