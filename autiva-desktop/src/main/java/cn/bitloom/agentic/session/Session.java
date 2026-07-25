package cn.bitloom.agentic.session;

import cn.bitloom.agentic.model.ModelTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话实体类，唯一的状态源。
 * <p>
 * 所有持久化字段直接在本类中定义，序列化为 metadata.json。
 * 瞬态字段（agent、messages、memoryManager）不参与序列化。
 * 编排逻辑（消息循环、记忆事件处理）由 SessionRunner 负责。
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    private String id;
    private String agentId;
    private String userId;
    private SessionTypeEnum sessionType;
    private SessionRespTypeEnum respType;
    private String source;
    private ModelTypeEnum model;

    /** 父会话ID（子 Session 关联父 Session） */
    private String parentId;

    @Builder.Default
    private String title = "新对话";

    @Builder.Default
    private Long createdAt = System.currentTimeMillis();

    @Builder.Default
    private Long updateAt = System.currentTimeMillis();

    // ===== 对话上下文 =====

    @Builder.Default
    private int messageCount = 0;

    @Builder.Default
    private int memoryCursor = 0;

    private String summary;

    // ===== 压缩上下文 =====

    @Builder.Default
    private int contextCapacity = 128000;

    @Builder.Default
    private double compactionThreshold = 0.8;

    @Builder.Default
    private int currentContextLength = 0;

}
