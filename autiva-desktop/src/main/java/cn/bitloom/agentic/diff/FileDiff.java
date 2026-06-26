package cn.bitloom.agentic.diff;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 文件 Diff 数据模型
 */
public record FileDiff(
        @JsonProperty("id") String id,
        @JsonProperty("filePath") String filePath,
        @JsonProperty("hunks") List<Hunk> hunks,
        @JsonProperty("isCreate") boolean isCreate,
        @JsonProperty("isDelete") boolean isDelete
) {
    @JsonCreator
    public FileDiff {
    }

    /**
     * Diff Hunk
     */
    public record Hunk(
            @JsonProperty("oldStart") int oldStart,
            @JsonProperty("oldCount") int oldCount,
            @JsonProperty("newStart") int newStart,
            @JsonProperty("newCount") int newCount,
            @JsonProperty("lines") List<DiffLine> lines
    ) {
        @JsonCreator
        public Hunk {
        }
    }

    /**
     * Diff 行
     */
    public record DiffLine(
            @JsonProperty("type") Type type,
            @JsonProperty("content") String content
    ) {
        @JsonCreator
        public DiffLine {
        }
    }

    /**
     * Diff 行类型
     */
    public enum Type {
        ADD,
        REMOVE,
        CONTEXT
    }
}
