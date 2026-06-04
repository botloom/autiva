package cn.bitloom.agentic.agent.subagent;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The interface Subagent executor.
 */
public interface SubagentExecutor {

    /**
     * Gets kind.
     *
     * @return the kind
     */
    String getKind();

    /**
     * Execute string.
     *
     * @param taskCall the task call
     * @param context  the context
     * @param subagent the subagent
     * @param onChunk  the on chunk
     * @return the string
     */
    String execute(TaskCall taskCall, Map<String, Object> context, SubagentDefinition subagent, Consumer<String> onChunk);

}
