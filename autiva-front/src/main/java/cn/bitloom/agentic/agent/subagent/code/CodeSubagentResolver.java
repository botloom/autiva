package cn.bitloom.agentic.agent.subagent.code;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentReference;
import cn.bitloom.agentic.agent.subagent.SubagentResolver;
import cn.bitloom.agentic.util.MarkdownParser;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.Assert;

public class CodeSubagentResolver implements SubagentResolver {

	@Override
	public boolean canResolve(SubagentReference subagentRef) {
		Assert.notNull(subagentRef, "SubagentReference不能为null");
		return subagentRef.kind().equals(CodeSubagentDefinition.KIND);
	}

	@Override
	public SubagentDefinition resolve(SubagentReference subagentRef) {
		Assert.notNull(subagentRef, "SubagentReference不能为null");
		Assert.isTrue(subagentRef.kind().equals(CodeSubagentDefinition.KIND),
				"CodeSubagentResolver只能解析类型为 " + CodeSubagentDefinition.KIND + " 的子代理");

		try {
			String uri = (subagentRef.uri().startsWith("/")) ? "file:" + subagentRef.uri() : subagentRef.uri();
			var resource = new DefaultResourceLoader().getResource(uri);
			String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
			MarkdownParser parser = new MarkdownParser(markdown);

			return new CodeSubagentDefinition(subagentRef, parser.getFrontMatter(), parser.getContent());
		}
		catch (IOException e) {
			throw new RuntimeException("读取任务文件失败: " + subagentRef.uri(), e);
		}
	}

}
