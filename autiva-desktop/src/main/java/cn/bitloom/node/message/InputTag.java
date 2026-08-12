package cn.bitloom.node.message;

import cn.bitloom.node.FileIconResolver;
import javafx.scene.input.DataFormat;

import java.io.File;
import java.nio.file.Path;

/**
 * 输入框 tag 数据模型。
 *
 * <p>表示对话框输入框中以 tag 形式展示的附加内容，支持三种来源：
 * <ul>
 *   <li>{@link Type#FILE}：从文件树/Diff 列表拖拽的文件，value 为绝对路径</li>
 *   <li>{@link Type#FILE_REF}：从文件编辑器拖拽的选中文本片段，value 形如
 *       {@code "filePath 第X行-第Y行"}，与原右键菜单行为一致</li>
 *   <li>{@link Type#TEXT}：从终端/Diff 等无法定位文件的区域拖拽的纯文本</li>
 * </ul>
 *
 * <p>{@code display} 为 tag 上展示的简短文本，{@code value} 为发送消息时拼接的原文。
 *
 * @param type      tag 类型
 * @param display   tag 展示文本
 * @param value     发送时拼接的值
 * @param iconPath  tag 图标 SVG 路径
 */
public record InputTag(Type type, String display, String value, String iconPath) {

    public enum Type { FILE, FILE_REF, TEXT }

    /**
     * 文件引用自定义 MIME 类型，用于拖拽源/目标之间传递
     * {@code "filePath|startLine|endLine"} 编码字符串。
     * 路径中不允许出现 {@code |} 字符，因此分隔符安全。
     */
    public static final DataFormat FILE_REF_FORMAT = new DataFormat("application/x-autiva-file-ref");

    /**
     * 编码文件引用为 {@code "filePath|startLine|endLine"} 字符串。
     */
    public static String encodeFileRef(Path filePath, int startLine, int endLine) {
        return filePath.toAbsolutePath() + "|" + startLine + "|" + endLine;
    }

    /**
     * 解码文件引用字符串为 {@link InputTag}。
     *
     * @param encoded 编码字符串
     * @return 解析失败返回 null
     */
    public static InputTag decodeFileRef(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        int lastBar = encoded.lastIndexOf('|');
        int prevBar = encoded.lastIndexOf('|', lastBar - 1);
        if (lastBar < 0 || prevBar < 0) {
            return null;
        }
        try {
            String pathStr = encoded.substring(0, prevBar);
            int startLine = Integer.parseInt(encoded.substring(prevBar + 1, lastBar));
            int endLine = Integer.parseInt(encoded.substring(lastBar + 1));
            return forFileRef(Path.of(pathStr), startLine, endLine);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 文件 tag（来自文件树/Diff 列表拖拽）。
     */
    public static InputTag forFile(File file) {
        String path = file.getAbsolutePath();
        String name = file.getName();
        return new InputTag(Type.FILE, name, path, FileIconResolver.resolveIconPath(name));
    }

    /**
     * 文件引用 tag（来自文件编辑器选中片段拖拽）。
     *
     * @param filePath  文件路径
     * @param startLine 起始行号（1-based）
     * @param endLine   结束行号（1-based）
     */
    public static InputTag forFileRef(Path filePath, int startLine, int endLine) {
        String path = filePath.toAbsolutePath().toString();
        String name = filePath.getFileName().toString();
        String display = name + " 第" + startLine + "-" + endLine + "行";
        String value = path + " 第" + startLine + "行-第" + endLine + "行";
        return new InputTag(Type.FILE_REF, display, value, FileIconResolver.resolveIconPath(name));
    }

    /**
     * 纯文本片段 tag（来自终端/Diff 等选区拖拽）。
     */
    public static InputTag forText(String text) {
        String display = collapseWhitespace(text);
        if (display.length() > 24) {
            display = display.substring(0, 24) + "…";
        }
        return new InputTag(Type.TEXT, display, text, FileIconResolver.textSnippetIconPath());
    }

    /**
     * 将换行/制表符等多余空白折叠为单个空格，便于 tag 展示。
     */
    private static String collapseWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
