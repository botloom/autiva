package cn.bitloom.agentic.agent.subagent.generic;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentReference;
import cn.bitloom.agentic.agent.subagent.SubagentResolver;
import cn.bitloom.agentic.util.MarkdownParser;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GenericSubagentResolver implements SubagentResolver {

	@Override
	public boolean canResolve(SubagentReference subagentRef) {
		return subagentRef.kind().equals(GenericSubagentDefinition.IDENTITY.name());
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

			return new GenericSubagentDefinition(subagentRef, parser.getFrontMatter(), parser.getContent());
		}
		catch (IOException e) {
			throw new RuntimeException("读取任务文件失败: " + subagentRef.uri(), e);
		}
	}

}
