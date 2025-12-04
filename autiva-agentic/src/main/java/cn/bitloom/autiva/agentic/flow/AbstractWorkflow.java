package cn.bitloom.autiva.agentic.flow;

import cn.bitloom.autiva.agentic.flow.config.WorkflowConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import reactor.core.publisher.Flux;

/**
 * 工作流接口，定义工作流的基本操作
 *
 * @author ningyu
 */
@Setter
@Getter
@RequiredArgsConstructor
public abstract class AbstractWorkflow {

    /**
     * The Workflow config.
     */
    protected final WorkflowConfig workflowConfig;
    /**
     * The Work flow context.
     */
    protected final WorkFlowContext workFlowContext;
    /**
     * The Parser.
     */
    protected final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 使用Flux启动工作流
     *
     * @return Flux<WorkFlowEvent> 工作流事件流
     */
    public abstract Flux<WorkFlowEvent> start();

    /**
     * 获取工作流名称
     *
     * @return 工作流名称
     */
    public abstract String getName();

    /**
     * 执行SpEL表达式
     *
     * @param script the script
     * @return the boolean
     */
    protected Boolean eval(String script){
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariables(this.workFlowContext.paramAsMap());
        Expression expression = this.parser.parseExpression(script);
        return expression.getValue(evalContext, Boolean.class);
    }

    /**
     * Stop.
     */
    public void stop(){
        this.workFlowContext.running.set(false);
    }
}