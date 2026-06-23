package cn.bitloom.agentic.evolve.runtime;

import cn.bitloom.agentic.evolve.gene.GeneRuntimeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StrategyGeneExecutor implements GeneExecutor {

    @Override
    public GeneRuntimeType supportedType() {
        return GeneRuntimeType.STRATEGY;
    }

    @Override
    public GeneResult execute(String code, String input) {
        long start = System.currentTimeMillis();
        try {
            String output = code != null ? code : "";
            return GeneResult.ok(output, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return GeneResult.fail(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
