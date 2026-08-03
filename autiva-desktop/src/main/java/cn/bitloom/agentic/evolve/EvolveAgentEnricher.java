package cn.bitloom.agentic.evolve;

import cn.bitloom.agentic.evolve.gene.GeneInjector;
import cn.bitloom.agentic.evolve.trajectory.TrajectoryRecorder;
import cn.bitloom.agentic.hook.IAgentHook;
import cn.bitloom.agentic.hook.PermissionHook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 增强辅助器，封装 Hook 和 Advisor 的条件注入逻辑。
 * <p>
 * {@link PermissionHook} 始终注入（权限拦截是基础能力）。
 * {@link TrajectoryRecorder} 和 {@link GeneInjector} 仅当 {@code app.evolve.enabled=true} 时注入。
 * <p>
 * 在所有 Agent 构建位置（AbstractHomePageViewModel、TaskTool、CronManager 等）调用
 * {@link #enrichAdvisors} 和 {@link #buildHooks} 即可完成条件注入，无需重复编写判断逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvolveAgentEnricher {

    private final PermissionHook permissionHook;
    private final ObjectProvider<TrajectoryRecorder> trajectoryRecorderProvider;
    private final ObjectProvider<GeneInjector> geneInjectorProvider;

    /**
     * 条件添加 GeneInjector 到 advisors 列表。
     * <p>
     * GeneInjector 的 order=220，在 SkillContextAdvisor(210) 之后执行。
     * 应在 advisors 列表构建完成后、传入 Agent.Builder 之前调用。
     *
     * @param advisors 待增强的 advisors 列表（会被原地修改）
     */
    public void enrichAdvisors(List<Advisor> advisors) {
        if (advisors == null) {
            return;
        }
        GeneInjector geneInjector = geneInjectorProvider.getIfAvailable();
        if (geneInjector != null) {
            advisors.add(geneInjector);
            log.debug("[EvolveAgentEnricher] 已注入 GeneInjector");
        }
    }

    /**
     * 构建 hooks 列表。
     * <p>
     * PermissionHook 始终注入（权限拦截是基础能力）。
     * TrajectoryRecorder 仅当进化启用时注入。
     *
     * @return hooks 列表
     */
    public List<IAgentHook> buildHooks() {
        List<IAgentHook> hooks = new ArrayList<>();
        // 权限 Hook 始终注入
        hooks.add(permissionHook);
        // 轨迹记录 Hook 仅进化启用时注入
        TrajectoryRecorder recorder = trajectoryRecorderProvider.getIfAvailable();
        if (recorder != null) {
            hooks.add(recorder);
            log.debug("[EvolveAgentEnricher] 已注入 TrajectoryRecorder");
        }
        return hooks;
    }
}
