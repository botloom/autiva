package cn.bitloom.agentic.agent.subagent.doctor;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentReference;
import cn.bitloom.agentic.agent.subagent.SubagentResolver;
import cn.bitloom.agentic.util.MarkdownParser;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DoctorSubagentResolver implements SubagentResolver {

    @Override
    public boolean canResolve(SubagentReference subagentRef) {
        return subagentRef.kind().equals(DoctorSubagentDefinition.IDENTITY.name());
    }

    @Override
    public SubagentDefinition resolve(SubagentReference subagentRef) {
        try {
            String uri = subagentRef.uri();
            if (new File(uri).isAbsolute()) {
                uri = "file:" + uri;
            }
            var resource = new DefaultResourceLoader().getResource(uri);
            String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
            MarkdownParser parser = new MarkdownParser(markdown);

            String name = parser.getFrontMatter().containsKey("name")
                    ? parser.getFrontMatter().get("name").toString()
                    : "Doctor";
            String description = parser.getFrontMatter().containsKey("description")
                    ? parser.getFrontMatter().get("description").toString()
                    : "系统医生";

            return new DoctorSubagentDefinition(subagentRef, name, description, parser.getContent());
        } catch (IOException e) {
            throw new RuntimeException("读取Doctor子智能体配置失败: " + subagentRef.uri(), e);
        }
    }
}
