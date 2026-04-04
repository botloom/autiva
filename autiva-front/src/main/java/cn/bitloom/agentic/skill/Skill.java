package cn.bitloom.agentic.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    private String name;
    private String description;
    private String license;
    private String compatibility;
    private Map<String, String> metadata;
    private String content;
    private Path filePath;

}
