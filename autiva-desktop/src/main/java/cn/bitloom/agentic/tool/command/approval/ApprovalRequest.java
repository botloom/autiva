package cn.bitloom.agentic.tool.command.approval;

/**
 * 批准请求 — 传给 UI 层展示给用户。
 *
 * <p>支持两种操作类型：
 * <ul>
 *   <li>{@code COMMAND}：命令执行批准（{@link CommandTool}），使用 {@link #command} + {@link #commandClass}</li>
 *   <li>{@code FILE}：文件写操作批准（WriteTool / EditTool），使用 {@link #toolName} + {@link #filePath} + {@link #action}</li>
 * </ul>
 *
 * @param operation    操作类型（COMMAND / FILE）
 * @param command      原始命令文本（仅 COMMAND 有值）
 * @param commandClass 命令分类（仅 COMMAND 有值；FILE 时固定为 WRITE）
 * @param reason       需要批准的原因（用于 UI 展示）
 * @param projectDir   项目目录（用于 UI 展示批准会写入哪个项目的 .autiva）
 * @param toolName     工具名（仅 FILE 有值，如 "Write" / "Edit"）
 * @param filePath     目标文件绝对路径（仅 FILE 有值）
 * @param action       文件动作描述（仅 FILE 有值，如 "创建" / "覆盖" / "编辑"）
 */
public record ApprovalRequest(
        String operation,
        String command,
        CommandClass commandClass,
        String reason,
        String projectDir,
        String toolName,
        String filePath,
        String action
) {

    /** 命令批准请求 */
    public static ApprovalRequest forCommand(String command, CommandClass commandClass, String reason, String projectDir) {
        return new ApprovalRequest("COMMAND", command, commandClass, reason, projectDir, null, null, null);
    }

    /** 文件写操作批准请求 */
    public static ApprovalRequest forFile(String toolName, String filePath, String action, String reason, String projectDir) {
        return new ApprovalRequest("FILE", null, CommandClass.WRITE, reason, projectDir, toolName, filePath, action);
    }
}
