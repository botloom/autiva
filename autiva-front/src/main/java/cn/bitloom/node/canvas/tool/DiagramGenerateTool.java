package cn.bitloom.node.canvas.tool;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 图表生成工具。
 * 根据文字描述生成结构化的图表元素数据，供画布渲染。
 * 使用 Builder 模式创建，不依赖 Spring 管理。
 */
@Slf4j
@Builder
public class DiagramGenerateTool {

    @Tool(description = "根据文字描述生成图表元素列表。" +
        "支持流程图、架构图、UML类图、时序图等。" +
        "返回JSON格式的元素数组，每个元素包含type、x、y、width、height、text等字段。" +
        "元素类型支持：rectangle、ellipse、diamond、arrow、line、text。" +
        "布局时请合理安排坐标，确保图表清晰可读。")
    public String generateDiagram(
            @ToolParam(description = "图表描述，如'用户登录流程图'或'微服务架构图'") String description,
            @ToolParam(description = "图表类型：flowchart/architecture/uml/sequence/er") String diagramType) {
        log.info("[ToolCall] DiagramGenerate - 生成图表: type={}, description={}", diagramType, description);

        return String.format("""
            请根据以下描述生成图表元素JSON：

            描述：%s
            图表类型：%s

            请输出JSON数组，格式如下：
            ```json
            [
              {"type": "rectangle", "x": 100, "y": 50, "width": 160, "height": 60, "text": "开始", "fillColor": "#a5d8ff"},
              {"type": "arrow", "x": 180, "y": 110, "width": 0, "height": 40},
              {"type": "diamond", "x": 80, "y": 150, "width": 200, "height": 100, "text": "判断条件"},
              {"type": "ellipse", "x": 100, "y": 300, "width": 160, "height": 60, "text": "结束", "fillColor": "#ffc9c9"}
            ]
            ```

            布局规则：
            1. 元素间距至少40px
            2. 箭头/线条的x,y是起点，width/height是终点偏移
            3. 矩形/椭圆/菱形包含text字段表示内部文字
            4. fillColor可选，用于区分不同类型节点
            5. 流程图从上到下布局，架构图从左到右布局
            """, description, diagramType);
    }
}
