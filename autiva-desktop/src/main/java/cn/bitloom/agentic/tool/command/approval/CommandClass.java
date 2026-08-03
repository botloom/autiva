package cn.bitloom.agentic.tool.command.approval;

/**
 * 命令分类 — 用于批准策略判断。
 * <ul>
 *   <li>{@link #READ} — 只读操作，不触发批准（dir/ls/cat/git status 等）</li>
 *   <li>{@link #WRITE} — 写操作，触发批准（rm/mv/git push/npm install 等）</li>
 *   <li>{@link #DESTRUCTIVE} — 破坏性操作，触发更严格的批准提示（rm -rf/git push --force/sudo 等）</li>
 * </ul>
 */
public enum CommandClass {
    READ,
    WRITE,
    DESTRUCTIVE
}
