package cn.bitloom.agentic.evolve.runtime;

import cn.bitloom.agentic.evolve.execution.ExecutionLog;
import cn.bitloom.agentic.evolve.execution.ExecutionRecorder;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneRuntimeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeneRuntime {

    private final Map<GeneRuntimeType, GeneExecutor> executors;
    private final ExecutionRecorder executionRecorder;

    public GeneRuntime(List<GeneExecutor> executorList, ExecutionRecorder executionRecorder) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(GeneExecutor::supportedType, Function.identity()));
        this.executionRecorder = executionRecorder;
    }

    public GeneResult execute(Gene gene, String input) {
        GeneExecutor executor = executors.get(gene.runtimeType());
        if (executor == null) {
            log.warn("[GeneRuntime] 未找到执行器: {}", gene.runtimeType());
            return GeneResult.fail("未找到执行器: " + gene.runtimeType(), 0);
        }

        String code = gene.code();
        if (code == null || code.isEmpty()) {
            code = gene.strategy() != null ? String.join("\n", gene.strategy()) : "";
        }

        long start = System.currentTimeMillis();
        GeneResult result = executor.execute(code, input);

        ExecutionLog execLog = result.success()
                ? ExecutionLog.success("exec_" + UUID.randomUUID().toString().substring(0, 8),
                "", "", gene.id(), input, result.output(), result.durationMs())
                : ExecutionLog.failure("exec_" + UUID.randomUUID().toString().substring(0, 8),
                "", "", gene.id(), input, result.error(), result.durationMs());
        executionRecorder.record(execLog);

        return result;
    }

    public GeneResult execute(Gene gene) {
        return execute(gene, "");
    }
}
