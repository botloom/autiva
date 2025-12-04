package cn.bitloom.autiva.agentic.flow;

import cn.bitloom.autiva.agentic.enums.ModelEnum;
import cn.bitloom.autiva.agentic.exception.WorkFlowException;
import cn.bitloom.autiva.agentic.flow.config.WorkflowConfig;
import cn.bitloom.autiva.agentic.flow.graph.Graph;
import cn.bitloom.autiva.agentic.flow.graph.VertexNode;
import cn.bitloom.autiva.agentic.flow.graph.VertexParam;
import cn.bitloom.autiva.agentic.util.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The type Graph workflow.
 *
 * @author ningyu
 */
@Slf4j
public class GraphWorkflow extends AbstractWorkflow {

    /**
     * Instantiates a new Graph workflow.
     *
     * @param workflowConfig  the workflow config
     * @param workFlowContext the work flow context
     */
    public GraphWorkflow(WorkflowConfig workflowConfig, WorkFlowContext workFlowContext) {
        super(workflowConfig, workFlowContext);
    }

    @Override
    public String getName() {
        return StringUtils.isNotBlank(workflowConfig.getName()) ? workflowConfig.getName() : this.getClass().getSimpleName();
    }

    @Override
    public Flux<WorkFlowEvent> start() {
        Graph graph = this.workflowConfig.getGraph();
        //找所有没有入弧的根节点
        List<VertexNode> rootVertexList = graph.getRootVertex();
        if (rootVertexList.isEmpty()) {
            return Flux.error(new WorkFlowException("未找到任何根节点（入弧为空）"));
        }
        //从根节点开始递归执行
        CopyOnWriteArraySet<String> executedNodeSet = new CopyOnWriteArraySet<>();
        List<Flux<WorkFlowEvent>> rootExecFluxList = rootVertexList.stream()
                .map(node -> this.exec(graph, node, executedNodeSet))
                .toList();
        return Flux
                .just(WorkFlowEvent.createStartEvent(this.getName()))
                .concatWith(Flux.merge(rootExecFluxList))
                .concatWith(Mono.just(WorkFlowEvent.createCompleteEvent()))
                .doFinally(signalType -> this.workFlowContext.clear());
    }

    private Flux<WorkFlowEvent> exec(Graph graph, VertexNode vertex, Set<String> executedNodeSet) {
        return Flux.defer(() -> {
            // 避免重复执行节点
            if (!executedNodeSet.add(vertex.getId())) {
                return Flux.empty();
            }

            if (!this.workFlowContext.running.get()) {
                return Flux.empty();
            }

            // 先执行所有前驱
            List<Flux<WorkFlowEvent>> preExecFluxList = graph.getInArc(vertex.getId()).stream()
                    .map(arc -> this.exec(graph, graph.getVertex(arc.getTailVexId()), executedNodeSet))
                    .toList();
            Flux<WorkFlowEvent> preExecFlux = preExecFluxList.isEmpty() ? Flux.empty() : Flux.merge(preExecFluxList);

            // 当前节点执行逻辑
            Flux<WorkFlowEvent> currExecFlux;

            VertexParam param = vertex.getParams();
            //判断当前节点是否满足用户自定义执行脚本条件
            boolean enableRun = graph.getInArc(vertex.getId()).stream()
                    .allMatch(arc -> {
                        if (Objects.isNull(arc.getParms())) {
                            return true;
                        }
                        if (StringUtils.isBlank(arc.getParms().getScript())) {
                            return true;
                        }
                        return this.eval(arc.getParms().getScript());
                    });
            if (!enableRun) {
                this.workFlowContext.running.set(false);
                currExecFlux = Flux.just(WorkFlowEvent.createStopEvent(this.workflowConfig.getName(), String.format("%s不满足用户自定义执行脚本条件", vertex.getName())));
                // 返回完整节点 Flux：前驱 -> 当前节点 -> empty
                return Flux.concat(preExecFlux, currExecFlux, Flux.empty());
            }

            currExecFlux = Flux.concat(
                            Mono.just(WorkFlowEvent.createNodeStartEvent(vertex.getId(), vertex.getName())),
                            Mono.defer(() -> SpringContextHolder.getBean(vertex.getType(), AbstractNode.class).run(param, workFlowContext))
                    )
                    .retryWhen(
                            Retry.fixedDelay(param.getRetryCount(), Duration.ofMillis(100))
                                    .doBeforeRetry(retrySignal -> {
                                        this.workFlowContext.putParam("model", ModelEnum.QWEN);
                                        log.error("节点重试:node:{},attempt:{}", vertex.getName(), (int) (retrySignal.totalRetriesInARow() + 1), retrySignal.failure());
                                    })
                    )
                    .onErrorResume(e -> {
                        this.workFlowContext.running.set(false);
                        //如果有重试且重试不成功，则取cause
                        if (Exceptions.isRetryExhausted(e)) {
                            e = e.getCause();
                        }
                        //根据不同的错误发送不同的错误提示语
                        if (e instanceof WorkFlowException) {

                            this.workFlowContext.running.set(false);
                            return Mono.just(WorkFlowEvent.createErrorEvent(e, vertex.getId(), vertex.getName()));
                        } else {
                            this.workFlowContext.clear();

                            this.workFlowContext.running.set(false);
                            return Mono.error(e);
                        }
                    });

            // 后继节点
            List<Flux<WorkFlowEvent>> nextExecFluxList = graph.getOutArc(vertex.getId()).stream()
                    .map(arc -> this.exec(graph, graph.getVertex(arc.getHeadVexId()), executedNodeSet))
                    .toList();
            Flux<WorkFlowEvent> nextExecFlux = nextExecFluxList.isEmpty() ? Flux.empty() : reactor.core.publisher.Flux.merge(nextExecFluxList);

            // 返回完整节点 Flux：前驱 -> 当前节点 -> 后继
            return Flux.concat(preExecFlux, currExecFlux, nextExecFlux);
        });
    }

}

