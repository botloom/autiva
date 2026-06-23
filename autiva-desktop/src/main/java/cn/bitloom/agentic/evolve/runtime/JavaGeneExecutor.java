package cn.bitloom.agentic.evolve.runtime;

import cn.bitloom.agentic.evolve.gene.GeneRuntimeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

@Slf4j
@Component
public class JavaGeneExecutor implements GeneExecutor {

    private final ScriptEngineManager engineManager = new ScriptEngineManager();

    @Override
    public GeneRuntimeType supportedType() {
        return GeneRuntimeType.JAVA;
    }

    @Override
    public GeneResult execute(String code, String input) {
        long start = System.currentTimeMillis();
        try {
            ScriptEngine engine = engineManager.getEngineByName("java");
            if (engine == null) {
                return GeneResult.fail("Java脚本引擎不可用", System.currentTimeMillis() - start);
            }

            SimpleBindings bindings = new SimpleBindings();
            bindings.put("input", input);

            Object result = engine.eval(code, bindings);
            String output = result != null ? result.toString() : "";

            return GeneResult.ok(output, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return GeneResult.fail("Java执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
