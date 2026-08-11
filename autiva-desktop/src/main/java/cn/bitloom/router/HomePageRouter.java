package cn.bitloom.router;

import cn.bitloom.constant.AgentMode;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.controller.AbstractHomePageController;
import cn.bitloom.controller.CoderEditorPanelController;
import cn.bitloom.controller.CodeHomePageController;
import cn.bitloom.controller.EditorPanelController;
import cn.bitloom.controller.IndexController;
import cn.bitloom.controller.WorkHomePageController;
import cn.bitloom.store.Store;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 首页与编辑器面板的模式路由器。
 * <p>
 * 启动时预加载 coder / work 两套 FXML（controller 为 Spring 单例，initialize 只调用一次），
 * 监听 {@link Store#currentAgent} 变化，根据 {@link AgentMode} 切换占位容器中的内容：
 * - coder 模式：CoderHomePage.fxml + CoderEditorPanel.fxml
 * - work 模式：WorkHomePage.fxml + EditorPanel.fxml
 * <p>
 * 切换时仅做 show/hide 与 toolUIBridge 回调重绑定，
 * 不重新加载 FXML（避免 initialize 重复注册全局监听器导致泄漏）。
 * 非活跃 viewModel 通过 dispose() 取消事件订阅，避免重复处理。
 */
@Slf4j
@Component
public class HomePageRouter {

    private final ApplicationContext applicationContext;
    private final ToolUIBridge toolUIBridge;

    @Getter
    private AbstractHomePageController activeHomeController;
    @Getter
    private EditorPanelController activeEditorController;

    private VBox coderHomeRoot;
    private VBox workHomeRoot;
    private VBox coderEditorRoot;
    private VBox workEditorRoot;

    private CodeHomePageController coderHomeController;
    private WorkHomePageController workHomeController;
    private CoderEditorPanelController coderEditorController;
    private EditorPanelController workEditorController;

    private VBox homePageSlot;
    private VBox editorPanelSlot;
    private AgentMode currentMode = null;

    public HomePageRouter(ApplicationContext applicationContext, ToolUIBridge toolUIBridge) {
        this.applicationContext = applicationContext;
        this.toolUIBridge = toolUIBridge;
    }

    /**
     * 绑定占位容器并预加载所有模式的 FXML。
     * 由 IndexController 在 initialize 时调用。
     */
    public void bind(IndexController indexController, VBox homePageSlot, VBox editorPanelSlot) {
        this.homePageSlot = homePageSlot;
        this.editorPanelSlot = editorPanelSlot;

        try {
            preloadAll(indexController);
        } catch (Exception e) {
            log.error("预加载 FXML 失败", e);
            throw new IllegalStateException("预加载 FXML 失败", e);
        }

        AgentMode initial = AgentMode.fromAgentId(Store.currentAgent.get());
        switchMode(initial);

        Store.currentAgent.addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> switchMode(AgentMode.fromAgentId(newVal))));
    }

    /**
     * 预加载所有模式的 FXML（每个 FXML 只加载一次，initialize 只触发一次）
     */
    private void preloadAll(IndexController indexController) throws Exception {
        coderHomeRoot = loadFxml("cn/bitloom/view/CoderHomePage.fxml");
        coderHomeController = (CodeHomePageController) getUserDataController(coderHomeRoot);
        coderHomeController.setIndexController(indexController);

        workHomeRoot = loadFxml("cn/bitloom/view/WorkHomePage.fxml");
        workHomeController = (WorkHomePageController) getUserDataController(workHomeRoot);
        workHomeController.setIndexController(indexController);

        coderEditorRoot = loadFxml("cn/bitloom/components/CoderEditorPanel.fxml");
        coderEditorController = (CoderEditorPanelController) getUserDataController(coderEditorRoot);
        coderEditorController.setIndexController(indexController);

        workEditorRoot = loadFxml("cn/bitloom/components/EditorPanel.fxml");
        workEditorController = (EditorPanelController) getUserDataController(workEditorRoot);
        workEditorController.setIndexController(indexController);
    }

    /**
     * 加载 FXML，使用 Spring ApplicationContext 作为 controllerFactory。
     * FXMLLoader 会将 controller 实例存入 root 的 UserData。
     */
    private VBox loadFxml(String path) throws Exception {
        FXMLLoader loader = new FXMLLoader(new ClassPathResource(path).getURL());
        loader.setControllerFactory(applicationContext::getBean);
        VBox root = loader.load();
        root.setUserData(loader.getController());
        return root;
    }

    private Object getUserDataController(VBox root) {
        return root.getUserData();
    }

    /**
     * 切换到指定模式。
     * 切换流程：
     * 1. dispose 旧 viewModel 事件订阅，隐藏旧 UI
     * 2. 替换占位容器内容为新模式 FXML
     * 3. 重绑定 toolUIBridge 回调
     * 4. 对新 viewModel 执行 createNewSession（重置 session）
     * 5. 对新 controller 执行 resetForNewSession（重置 UI）
     */
    private void switchMode(AgentMode mode) {
        if (mode == currentMode) {
            return;
        }
        currentMode = mode;

        // 取消非活跃 viewModel 的事件订阅
        if (activeHomeController != null) {
            activeHomeController.getViewModel().dispose();
            activeHomeController.hide();
        }

        if (mode == AgentMode.CODE) {
            activeHomeController = coderHomeController;
            activeEditorController = coderEditorController;
            homePageSlot.getChildren().setAll(coderHomeRoot);
            editorPanelSlot.getChildren().setAll(coderEditorRoot);
            VBox.setVgrow(coderHomeRoot, Priority.ALWAYS);
            VBox.setVgrow(coderEditorRoot, Priority.ALWAYS);
        } else {
            activeHomeController = workHomeController;
            activeEditorController = workEditorController;
            homePageSlot.getChildren().setAll(workHomeRoot);
            editorPanelSlot.getChildren().setAll(workEditorRoot);
            VBox.setVgrow(workHomeRoot, Priority.ALWAYS);
            VBox.setVgrow(workEditorRoot, Priority.ALWAYS);
        }

        // 重绑定 toolUIBridge 回调到活跃 controller
        toolUIBridge.setOnNodeAdded(activeHomeController::addChatNode);
        activeHomeController.show();

        // 模式切换后重置新 viewModel 与 UI（确保新会话初始态干净）
        activeHomeController.getViewModel().createNewSession();
        activeHomeController.resetForNewSession();
    }
}
