package cn.bitloom.agentic.agent.subagent;

import java.util.function.Consumer;

public interface SubagentExecutor {

	String getKind();

	String execute(TaskCall taskCall, SubagentDefinition subagent);

	default String execute(TaskCall taskCall, SubagentDefinition subagent, Consumer<String> onChunk) {
		String result = execute(taskCall, subagent);
		if (onChunk != null) {
			onChunk.accept(result);
		}
		return result;
	}

}
