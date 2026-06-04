package cn.bitloom.agentic.util;

import cn.bitloom.agentic.tool.core.TodoWriteTool.TodoEventHandler;
import cn.bitloom.agentic.tool.core.TodoWriteTool.Todos;
import cn.bitloom.store.ToolUIBridge;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GuiTodoEventHandler implements TodoEventHandler {

    private final ToolUIBridge toolUIBridge;

    @Override
    public void handle(Todos todos) {
        try {
            String todosJson = JSON.toJSONString(todos);
            this.toolUIBridge.showTodos(todosJson);
            log.debug("Todo list displayed in UI: {} items", todos.todos().size());
        } catch (Exception e) {
            log.error("Error displaying todos in UI", e);
        }
    }
}
