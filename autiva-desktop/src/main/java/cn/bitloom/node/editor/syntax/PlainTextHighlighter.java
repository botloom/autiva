package cn.bitloom.node.editor.syntax;

import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * 纯文本高亮器，不做任何高亮，作为未知扩展名的兜底实现。
 */
public final class PlainTextHighlighter implements SyntaxHighlighter {

    @Override
    public void apply(StyleClassedTextArea area, String text) {
        // 不应用任何样式
    }
}
