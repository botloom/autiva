package cn.bitloom.agentic.util;

import cn.bitloom.agentic.tool.interaction.TodoWriteTool.TodoEventHandler;
import cn.bitloom.agentic.tool.interaction.TodoWriteTool.Todos;
import cn.bitloom.bridge.desktop.ToolUIBridge;
import cn.bitloom.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GuiTodoEventHandler implements TodoEventHandler {

    private final ToolUIBridge toolUIBridge;

    @Override
    public void handle(Todos todos, String sessionId) {
        try {
            String todosJson = JsonUtils.toJson(todos);
            this.toolUIBridge.showTodos(todosJson, sessionId);
            log.debug("Todo list displayed in UI: {} items", todos.todos().size());
        } catch (Exception e) {
            log.error("Error displaying todos in UI", e);
        }
    }
}
