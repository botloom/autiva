package cn.bitloom.node.editor.syntax;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 语法高亮器。
 *
 * <p>识别标题、代码块、行内代码、加粗、斜体、链接、图片、
 * 引用、列表项、水平线。
 */
public final class MarkdownSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String CODE_BLOCK = "```[\\s\\S]*?```";
    private static final String HEADING = "(?m)^#{1,6}\\s+[^\\n]+";
    private static final String BLOCKQUOTE = "(?m)^>\\s*[^\\n]*";
    private static final String HORIZONTAL_RULE = "(?m)^-{3,}$|^\\*{3,}$";
    private static final String LIST_ITEM = "(?m)^\\s*(?:[-*+]|\\d+\\.)\\s+[^\\n]*";
    private static final String IMAGE = "!\\[[^\\]]*\\]\\([^)]+\\)";
    private static final String LINK = "\\[[^\\]]+\\]\\([^)]+\\)";
    private static final String BOLD = "\\*\\*[^*]+?\\*\\*|__[^_]+?__";
    private static final String ITALIC = "\\*[^*\\n]+?\\*|_[^_\\n]+?_";
    private static final String INLINE_CODE = "`[^`\\n]+`";

    @Override
    protected int getFlags() {
        return Pattern.MULTILINE;
    }

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(CODE_BLOCK, "syntax-string"),
                TokenGroup.of(HEADING, "syntax-keyword"),
                TokenGroup.of(BLOCKQUOTE, "syntax-comment"),
                TokenGroup.of(HORIZONTAL_RULE, "syntax-operator"),
                TokenGroup.of(LIST_ITEM, "syntax-annotation"),
                TokenGroup.of(IMAGE, "syntax-annotation"),
                TokenGroup.of(LINK, "syntax-type"),
                TokenGroup.of(BOLD, "syntax-keyword"),
                TokenGroup.of(ITALIC, "syntax-literal"),
                TokenGroup.of(INLINE_CODE, "syntax-string")
        );
    }
}
