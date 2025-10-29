package cn.bitloom.autiva.agentic.flow;


import cn.bitloom.autiva.agentic.exception.WorkFlowException;
import cn.bitloom.autiva.agentic.flow.graph.Graph;
import cn.bitloom.autiva.agentic.flow.graph.VertexNode;
import cn.bitloom.autiva.agentic.flow.graph.VertexParam;
import cn.bitloom.autiva.agentic.util.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The type Graph workflow.
 *
 * @author ningyu
 */
@Slf4j
public class GraphWorkflow implements IWorkflow {

    private final WorkflowConfig workflowConfig;
    private final WorkFlowContext workFlowContext;
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * Instantiates a new Graph workflow.
     *
     * @param workflowConfig  the workflow config
     * @param workFlowContext the work flow context
     */
    public GraphWorkflow(WorkflowConfig workflowConfig, WorkFlowContext workFlowContext) {
        this.workflowConfig = workflowConfig;
        this.workFlowContext = workFlowContext;
    }

    @Override
    public String getName() {
        return StringUtils.isNotBlank(workflowConfig.getName()) ? workflowConfig.getName() : this.getClass().getSimpleName();
    }

    @Override
    public Flux<WorkFlowEvent> start() {
        Graph graph = this.workflowConfig.getGraph();
        //工作流开始事件
        Flux<WorkFlowEvent> result = Flux.just(WorkFlowEvent.createStartEvent(this.workflowConfig.getName()));
        //找所有没有入弧的根节点
        List<VertexNode> rootVertex = graph.getRootVertex();
        if (rootVertex.isEmpty()) {
            return Flux.error(new WorkFlowException("未找到任何根节点（入弧为空）"));
        }
        //从根节点开始递归执行
        List<Flux<WorkFlowEvent>> rootExecutions = rootVertex.stream()
                .map(node -> this.exec(graph, node, new ConcurrentSkipListSet<>()))
                .toList();
        return result
                .concatWith(Flux.merge(rootExecutions))
                .concatWith(Mono.defer(() -> Mono.just(WorkFlowEvent.createStopEvent(workflowConfig.getName()))))
                .onErrorResume(error -> Flux.just(WorkFlowEvent.createErrorEvent(error, null, null)))
                .doFinally(signalType -> this.workFlowContext.clear());
    }

    private Flux<WorkFlowEvent> exec(Graph graph, VertexNode vertex, Set<String> executedNodes) {
        if (!this.running.get()) {
            return Flux.just(WorkFlowEvent.createStopEvent(this.workflowConfig.getName()));
        }

        // 避免重复执行节点
        if (!executedNodes.add(vertex.getId())) {
            return Flux.empty();
        }

        // 先执行所有前驱
        List<Flux<WorkFlowEvent>> preFlows = graph.getInArc(vertex.getId()).stream()
                .map(arc -> {
                    VertexNode prev = graph.getVertex(arc.getTailVexId());
                    return exec(graph, prev, executedNodes);
                })
                .toList();
        Flux<WorkFlowEvent> preFlux = preFlows.isEmpty() ? Flux.empty() : Flux.merge(preFlows);

        // 当前节点执行逻辑
        Flux<WorkFlowEvent> currFlux = Flux.defer(() -> {
            VertexParam param = vertex.getParams();
            boolean enabled = param == null || param.getEnabled() == null || param.getEnabled();
            if (!enabled) {
                return Flux.empty();
            }

            if (!this.running.get()) {
                return Flux.just(WorkFlowEvent.createStopEvent(this.workflowConfig.getName()));
            }

            List<WorkFlowEvent> startEvent = List.of(
                    WorkFlowEvent.createNodeStartEvent(vertex.getId(), vertex.getName())
            );
            AbstractNode node = SpringContextHolder.getBean(vertex.getType(), AbstractNode.class);

            int retryCount = param == null || param.getRetryCount() == null ? 0 : param.getRetryCount();
            long timeoutMillis = param == null || param.getTimeout() == null ? 0L : param.getTimeout();

            Mono<Map<String, Object>> nodeMono = Mono.fromCallable(() -> node.run(param, workFlowContext));

            if (timeoutMillis > 0) {
                nodeMono = nodeMono.timeout(Duration.ofMillis(timeoutMillis));
            }

            if (retryCount > 0) {
                nodeMono = nodeMono.retryWhen(
                        reactor.util.retry.Retry.fixedDelay(retryCount, Duration.ofMillis(100))
                                .doBeforeRetry(retrySignal -> log.error("节点重试:node:{},attempt:{}", vertex.getName(), (int) (retrySignal.totalRetriesInARow() + 1), retrySignal.failure()))
                );
            }

            return Flux.concat(
                    Flux.fromIterable(startEvent),
                    nodeMono
                            .map(result -> WorkFlowEvent.createNodeCompleteEvent(vertex.getId(), vertex.getName(), result))
                            .onErrorResume(e -> {
                                this.running.set(false);
                                return Mono.just(WorkFlowEvent.createErrorEvent(e, vertex.getId(), vertex.getName()));
                            })
                            .flux()
            );
        });

        // 后继节点
        List<Flux<WorkFlowEvent>> nextFlows = graph.getOutArc(vertex.getId()).stream()
                .map(arc -> {
                    VertexNode next = graph.getVertex(arc.getHeadVexId());
                    return exec(graph, next, executedNodes);
                })
                .toList();
        Flux<WorkFlowEvent> nextFlux = nextFlows.isEmpty() ? Flux.empty() : Flux.merge(nextFlows);

        // 返回完整节点 Flux：前驱 -> 当前节点 -> 后继
        return Flux.concat(preFlux, currFlux, nextFlux);
    }


}
