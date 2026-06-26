package cn.bitloom.node.editor.syntax;

import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * 语法高亮器接口。
 *
 * <p>基于 RichTextFX 的 {@link StyleClassedTextArea#setStyleClass(int, int, String)}
 * 为文本区域中的字符区间应用 CSS 样式类，从而实现代码语法高亮。
 *
 * <p>所有实现应当是线程安全的（无状态或使用不可变内部状态），
 * 调用方负责在 JavaFX Application Thread 上调用 {@link #apply}。
 */
public interface SyntaxHighlighter {

    /**
     * 为文本区域应用语法高亮。
     *
     * <p>调用前调用方应已经通过 {@code replaceText} 设置完整文本，
     * 本方法只负责计算并应用样式区间，不修改文本内容。
     *
     * @param area 目标文本区域（已包含待高亮文本）
     * @param text 待高亮的全文
     */
    void apply(StyleClassedTextArea area, String text);
}
