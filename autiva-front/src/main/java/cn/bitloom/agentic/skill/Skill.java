package cn.bitloom.agentic.skill;

import java.util.Map;
import java.util.stream.Collectors;

public record Skill(String basePath, Map<String, Object> frontMatter, String content) {

    public String name() {
        Object name = this.frontMatter.get("name");
        return name != null ? name.toString() : null;
    }

    public String description() {
        Object desc = this.frontMatter.get("description");
        return desc != null ? desc.toString() : null;
    }

    public String toXml() {
        String frontMatterXml = this.frontMatter
                .entrySet()
                .stream()
                .map(e -> "  <%s>%s</%s>".formatted(e.getKey(), e.getValue(), e.getKey()))
                .collect(Collectors.joining("\n"));

        return "<skill>\n  <basePath>%s</basePath>\n%s\n</skill>".formatted(this.basePath, frontMatterXml);
    }

}
