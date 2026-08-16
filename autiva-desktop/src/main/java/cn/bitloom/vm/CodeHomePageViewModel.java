package cn.bitloom.vm;

import cn.bitloom.project.ProjectInfo;
import cn.bitloom.project.ProjectRegistry;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.agentic.tool.plan.ExitPlanModeTool;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.node.SlashCommands;
import cn.bitloom.node.tool.PlanApprovalCard;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Coder 模式首页 ViewModel。
 * <p>
 * 在通用会话管理基础上增加项目上下文管理：
 * - currentProject 属性（供 Controller 监听）
 * - 项目列表/新建/注册本地文件夹
 * - buildMessageWithContext 直接返回原文（项目规则由 system prompt 注入）
 * - onSwitchAgent 非 coder 时清空 currentProject
 * - onSessionSwitched 从 session.metadata 恢复 currentProject
 * - slash 命令系统：/goal（目标闭环）、/plan（计划模式，批准后自动执行）
 */
@Slf4j
@Component
public class CodeHomePageViewModel extends AbstractHomePageViewModel {

    private final ProjectRegistry projectRegistry;
    private final ObjectProperty<ProjectInfo> currentProject = new SimpleObjectProperty<>();

    /** 已批准待执行的计划（批准时记录，当前计划模式流结束后自动发起执行轮） */
    private volatile PendingPlan pendingPlan;

    private record PendingPlan(String sessionId, String plan) {
    }

    public CodeHomePageViewModel(FileSystemSessionManager fileSystemSessionManager,
                                 AgentDefinitionManager definitionManager,
                                 ModelFactory modelFactory,
                                 Toolkit toolkit,
                                 cn.bitloom.agentic.skill.SkillManager skillManager,
                                 List<cn.bitloom.agentic.permission.strategy.ToolApprovalStrategy> approvalStrategies,
                                 cn.bitloom.config.ConfigManager configManager,
                                 cn.bitloom.agentic.tool.mcp.McpConnectionManager mcpConnectionManager,
                                 cn.bitloom.agentic.goal.GoalManager goalManager,
                                 cn.bitloom.bridge.desktop.ToolUIBridge toolUIBridge,
                                 ProjectRegistry projectRegistry) {
        super(fileSystemSessionManager, definitionManager, modelFactory, toolkit, skillManager, approvalStrategies,
                configManager, mcpConnectionManager, goalManager, toolUIBridge);
        this.projectRegistry = projectRegistry;
    }

    /**
     * 当前项目属性（供 Controller 监听）
     */
    public ObjectProperty<ProjectInfo> currentProjectProperty() {
        return currentProject;
    }

    public void setCurrentProject(ProjectInfo project) {
        currentProject.set(project);
    }

    @Override
    public ProjectInfo getCurrentProject() {
        return currentProject.get();
    }

    /**
     * code 模式按 session.metadata 的 projectId 解析 projectPath：
     * Goal 自动续轮时 goal session 可能已非 active，不能依赖当前 UI 的 currentProject。
     */
    @Override
    protected String resolveProjectPath(cn.bitloom.agentic.session.Session session) {
        Object projectId = session.metadata() != null ? session.metadata().get("projectId") : null;
        if (projectId != null) {
            return projectRegistry.findById(projectId.toString())
                    .map(ProjectInfo::path)
                    .orElse(null);
        }
        return null;
    }

    /**
     * code 模式前置拦截：未选项目时不发话，提示用户。
     */
    @Override
    public void sendMessage(String text) {
        if (currentProject.get() == null) {
            Platform.runLater(() -> Store.warnMessage.set("请先选择项目再开始对话"));
            return;
        }
        super.sendMessage(text);
    }

    // ===== Slash 命令系统（/goal /plan） =====

    @Override
    public boolean isSlashCommandSupported() {
        return true;
    }

    @Override
    public boolean handleSlashCommand(String text) {
        SlashCommands.Parsed parsed = SlashCommands.parse(text);
        if (parsed == null) {
            return false;
        }
        switch (parsed.name()) {
            case "goal" -> handleGoalCommand(parsed.args());
            case "plan" -> handlePlanCommand();
            default -> Store.warnMessage.set(
                    "未知命令 /" + parsed.name() + "，可用命令：/goal <目标描述>、/plan");
        }
        return true;
    }

    /**
     * /goal：直接激活目标闭环（不经 LLM）。目标需包含结束状态 + 验证方式 + 限制条件。
     */
    private void handleGoalCommand(String args) {
        if (currentProject.get() == null) {
            Store.warnMessage.set("请先选择项目再设置目标");
            return;
        }
        if (args == null || args.isBlank()) {
            Store.warnMessage.set("用法：/goal <目标描述：结束状态 + 验证方式 + 限制条件>");
            return;
        }
        Session s = ensureSession();
        cn.bitloom.agentic.goal.GoalState state = goalManager.setGoal(s.id(), args.trim());
        onGoalUpdated(s.id(), state);
    }

    /**
     * /plan：切换计划模式。切换后 evict 当前 session 的 Agent，
     * 下一次消息按新模式重建（计划模式：只读工具 + ExitPlanMode）。
     */
    private void handlePlanCommand() {
        if (currentProject.get() == null) {
            Store.warnMessage.set("请先选择项目再使用计划模式");
            return;
        }
        boolean enter = !isPlanMode();
        planModeProperty().set(enter);
        if (session != null) {
            evictAgent(session.id());
        }
        log.info("[Plan] 计划模式已{}: session={}", enter ? "开启" : "关闭",
                session != null ? session.id() : "(未创建)");
    }

    // ===== 计划批准流程（Plan Mode 闭环） =====

    /**
     * 智能体提交计划（ExitPlanModeTool 回调，工具线程）：
     * 经 ToolUIBridge 显示批准卡片，用户决策经 future 返回给工具。
     */
    @Override
    protected void onPlanSubmitted(String sessionId, String plan, CompletableFuture<String> future) {
        Platform.runLater(() -> {
            PlanApprovalCard card = new PlanApprovalCard(plan, decision -> onPlanDecided(sessionId, plan, decision, future));
            toolUIBridge.showPlanApproval(card);
        });
    }

    /** 用户对计划做出决策（FX 线程） */
    private void onPlanDecided(String sessionId, String plan, String decision, CompletableFuture<String> future) {
        if (ExitPlanModeTool.DECISION_APPROVED.equals(decision)) {
            // 批准：退出计划模式 + evict Agent（重建为全工具），当前流结束后自动执行
            planModeProperty().set(false);
            evictAgent(sessionId);
            this.pendingPlan = new PendingPlan(sessionId, plan);
            log.info("[Plan] 计划已批准，待当前流结束后自动执行: session={}", sessionId);
        } else if (ExitPlanModeTool.DECISION_ABANDONED.equals(decision)) {
            planModeProperty().set(false);
            evictAgent(sessionId);
            log.info("[Plan] 计划已放弃，退出计划模式: session={}", sessionId);
        }
        future.complete(decision);
    }

    /**
     * 流结束回调：批准后的计划在计划模式流收尾后自动发起执行轮
     * （新 Agent 已按非计划模式重建，具备完整工具）。
     */
    @Override
    protected void onStreamCompleted(String sessionId) {
        PendingPlan pp = this.pendingPlan;
        if (pp == null || !pp.sessionId().equals(sessionId)) {
            return;
        }
        this.pendingPlan = null;
        String message = "计划已获用户批准。请立即严格按以下计划逐项执行，无需再次确认：\n\n" + pp.plan();
        continueRound(sessionId, message);
    }

    public List<ProjectInfo> listProjects() {
        return projectRegistry.listProjects();
    }

    public ProjectInfo createNewProject(String name) throws java.io.IOException {
        ProjectInfo project = projectRegistry.createProject(name);
        currentProject.set(project);
        return project;
    }

    public void registerLocalProject(String path, String name) throws java.io.IOException {
        ProjectInfo project = projectRegistry.registerLocal(path, name);
        currentProject.set(project);
    }

    @Override
    protected String buildMessageWithContext(String text) {
        // 项目规则已通过 system prompt（AUTIVA.md）注入，消息中不再附加项目前缀
        return text;
    }

    @Override
    protected void onSwitchAgent(String agentId) {
        if (AgentMode.fromAgentId(agentId) != AgentMode.CODE) {
            currentProject.set(null);
        }
    }

    /**
     * 切换历史会话时从 session.metadata 恢复 currentProject。
     * 确保 coder 模式下打开历史会话后项目选择不为空。
     */
    @Override
    protected void onSessionSwitched(Session session) {
        Object projectIdObj = session.metadata().get("projectId");
        if (projectIdObj != null) {
            projectRegistry.findById(projectIdObj.toString())
                    .ifPresentOrElse(
                            this::setCurrentProject,
                            () -> {
                                log.warn("会话关联的项目已不存在: projectId={}", projectIdObj);
                                currentProject.set(null);
                            }
                    );
        } else {
            currentProject.set(null);
        }
    }
}
