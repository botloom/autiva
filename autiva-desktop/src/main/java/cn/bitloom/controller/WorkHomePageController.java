package cn.bitloom.controller;

import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.holder.ButtonBarHolder;
import cn.bitloom.vm.AbstractHomePageViewModel;
import cn.bitloom.vm.WorkHomePageViewModel;
import cn.bitloom.window.WindowManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Work 模式首页控制器。
 * <p>
 * 当前为空壳实现，仅提供通用按钮配置（newChat/tool/todo）。
 * 预留扩展点供未来 work 模式专有 UI 和逻辑。
 */
@Slf4j
@Component
public class WorkHomePageController extends AbstractHomePageController {

    private final WorkHomePageViewModel viewModel;

    public WorkHomePageController(ToolUIBridge toolUIBridge,
                                  WindowManager windowManager,
                                  WorkHomePageViewModel viewModel) {
        super(toolUIBridge, windowManager);
        this.viewModel = viewModel;
    }

    @Override
    public AbstractHomePageViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public List<ButtonBarHolder.ButtonConfig> getButtonConfigs() {
        return createCommonButtons();
    }

    @Override
    protected void onResetForNewSession() {
        // work 模式无专有重置逻辑
    }
}
