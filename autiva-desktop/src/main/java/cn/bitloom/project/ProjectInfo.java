package cn.bitloom.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 项目信息数据模型
 *
 * @param id        项目唯一ID
 * @param name      项目名称
 * @param path      项目本地路径
 * @param gitBranch 当前 Git 分支（可为 null）
 * @param createdAt 创建时间
 */
public record ProjectInfo(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("path") String path,
        @JsonProperty("gitBranch") String gitBranch,
        @JsonProperty("createdAt") Instant createdAt
) {
    @JsonCreator
    public ProjectInfo {
    }
}
