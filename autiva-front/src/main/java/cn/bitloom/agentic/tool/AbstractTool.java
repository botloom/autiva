package cn.bitloom.agentic.tool;

import lombok.Getter;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

/**
 * 工具抽象基类，借鉴 AgentScope 的 ToolBase 设计模式。
 * <p>
 * 所有工具统一继承此类，通过泛型 {@code I} 指定输入参数 record 类型，
 * 实现 {@link #execute} 方法定义执行逻辑，通过 {@link #toToolCallback()} 转换为 Spring AI 的 ToolCallback。
 * <p>
 * 使用方式：
 * <pre>
 * public class ReadTool extends AbstractTool&lt;ReadTool.Input&gt; {
 *     public record Input(
 *         @ToolParam(description = "文件路径") String filePath
 *     ) {}
 *
 *     private ReadTool() {
 *         super("Read", "读取文件", Input.class);
 *     }
 *
 *     &#64;Override
 *     public ToolResult execute(Input input, ToolContext context) {
 *         return ToolResult.success("读取成功");
 *     }
 *
 *     public static Builder builder() { return new Builder(); }
 *     public static class Builder {
 *         public ReadTool build() { return new ReadTool(); }
 *     }
 * }
 * </pre>
 *
 * @param <I> 输入参数 record 类型，字段使用 {@link ToolParam} 注解
 */
@Getter
public abstract class AbstractTool<I> {

    private final String name;
    private final String description;
    private final Class<I> inputType;

    protected AbstractTool(String name, String description, Class<I> inputType) {
        this.name = name;
        this.description = description;
        this.inputType = inputType;
    }

    /**
     * 工具执行逻辑，子类实现。
     *
     * @param input   输入参数 record 实例
     * @param context Spring AI 工具上下文，可从中获取 sessionId 等信息
     * @return 统一返回值
     */
    public abstract ToolResult execute(I input, ToolContext context);

    /**
     * 转换为 Spring AI 的 ToolCallback，用于注册到 ChatClient。
     * <p>
     * 自动将 {@link #execute} 的 ToolResult 返回值转为 JSON 字符串。
     */
    public final ToolCallback toToolCallback() {
        BiFunction<I, ToolContext, String> fn = (input, context) -> execute(input, context).toJson();
        return FunctionToolCallback.builder(name, fn)
                .description(description)
                .inputType(inputType)
                .build();
    }

}
