package cn.bitloom.pet;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 萌宠状态数据类，持久化到 ~/.autiva/pet/state.json。
 */
public class PetState {

    private PetType petType = PetType.SUNFLOWER;
    private int totalMessages = 0;
    private double growthProgress = 0.0;
    private long createdAt = System.currentTimeMillis();
    private double posX = -1;
    private double posY = -1;

    @JsonIgnore
    private double avgLength;
    @JsonIgnore
    private double codeRatio;
    @JsonIgnore
    private double emojiRate;
    @JsonIgnore
    private double frequency;
    @JsonIgnore
    private double diversity;

    public PetType getPetType() {
        return petType;
    }

    public void setPetType(PetType petType) {
        this.petType = petType;
    }

    public int getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(int totalMessages) {
        this.totalMessages = totalMessages;
    }

    public double getGrowthProgress() {
        return growthProgress;
    }

    public void setGrowthProgress(double growthProgress) {
        this.growthProgress = growthProgress;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public double getPosX() {
        return posX;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public double getPosY() {
        return posY;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public double getAvgLength() {
        return avgLength;
    }

    public void setAvgLength(double avgLength) {
        this.avgLength = avgLength;
    }

    public double getCodeRatio() {
        return codeRatio;
    }

    public void setCodeRatio(double codeRatio) {
        this.codeRatio = codeRatio;
    }

    public double getEmojiRate() {
        return emojiRate;
    }

    public void setEmojiRate(double emojiRate) {
        this.emojiRate = emojiRate;
    }

    public double getFrequency() {
        return frequency;
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }

    public double getDiversity() {
        return diversity;
    }

    public void setDiversity(double diversity) {
        this.diversity = diversity;
    }

    /**
     * 获取当前生长阶段。
     */
    public GrowthStage getGrowthStage() {
        return GrowthStage.fromMessageCount(totalMessages);
    }

    /**
     * 计算当前阶段内的进度。
     */
    public double getStageProgress() {
        return getGrowthStage().getProgress(totalMessages);
    }
}
