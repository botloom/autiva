package cn.bitloom.vm;

import cn.bitloom.project.ProjectInfo;
import cn.bitloom.project.ProjectRegistry;
import cn.bitloom.agentic.agent.AgentDefinitionManager;
import cn.bitloom.agentic.model.ModelFactory;
import cn.bitloom.agentic.session.FileSystemSessionManager;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.tool.Toolkit;
import cn.bitloom.constant.AgentMode;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Coder 模式首页 ViewModel。
 * <p>
 * 在通用会话管理基础上增加项目上下文管理：
 * - currentProject 属性（供 Controller 监听）
 * - 项目列表/新建/注册本地文件夹
 * - buildMessageWithContext 直接返回原文（项目规则由 system prompt 注入）
 * - onSwitchAgent 非 coder 时清空 currentProject
 * - onSessionSwitched 从 session.metadata 恢复 currentProject
 */
@Slf4j
@Component
public class CodeHomePageViewModel extends AbstractHomePageViewModel {

    private final ProjectRegistry projectRegistry;
    private final ObjectProperty<ProjectInfo> currentProject = new SimpleObjectProperty<>();

    public CodeHomePageViewModel(FileSystemSessionManager fileSystemSessionManager,
                                 AgentDefinitionManager definitionManager,
                                 ModelFactory modelFactory,
                                 Toolkit toolkit,
                                 cn.bitloom.agentic.skill.SkillManager skillManager,
                                 cn.bitloom.agentic.evolve.EvolveAgentEnricher evolveEnricher,
                                 ProjectRegistry projectRegistry) {
        super(fileSystemSessionManager, definitionManager, modelFactory, toolkit, skillManager, evolveEnricher);
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
