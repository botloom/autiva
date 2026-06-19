package cn.bitloom.agentic.evolve.solidify;

import cn.bitloom.exception.EvolveException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CanaryCheck {

    private static final List<String> CRITICAL_MODULES = List.of(
            "cn.bitloom.agentic.agent.Agent",
            "cn.bitloom.agentic.agent.AgentDefinitionManager",
            "cn.bitloom.agentic.evolve.EvolutionEngine"
    );

    public CanaryResult check() {
        for (String module : CRITICAL_MODULES) {
            try {
                Class.forName(module);
            } catch (ClassNotFoundException e) {
                log.warn("[Evolve] 金丝雀检查: 核心模块不可加载: {}", module);
                return new CanaryResult(false, EvolveException.canaryFailed(module).getMessage());
            }
        }
        log.info("[Evolve] 金丝雀检查通过");
        return new CanaryResult(true, "所有核心模块可正常加载");
    }

    public record CanaryResult(boolean passed, String message) {
    }
}
