package cn.bitloom.node.editor.syntax;

import java.util.List;

/**
 * Python 语法高亮器。
 *
 * <p>识别注释、字符串（含三引号、f/r/b 前缀）、关键字、数字、装饰器。
 * 多行字符串使用非贪婪匹配，避免吞掉后续内容。
 */
public final class PythonSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String KEYWORDS =
            "\\b(?:False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|"
                    + "except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|"
                    + "return|try|while|with|yield|match|case)\\b";

    private static final String COMMENT = "#[^\\n]*";
    private static final String TRIPLE_DOUBLE = "\"\"\"(?:[^\"]|\"(?!\"\"))*?\"\"\"";
    private static final String TRIPLE_SINGLE = "'''(?:[^']|'(?!''))*?'''";
    private static final String PREFIX_STRING = "[fFrRbBuU]{0,2}\"(?:\\\\.|[^\"\\\\])*\"";
    private static final String PREFIX_CHAR = "[fFrRbBuU]{0,2}'(?:\\\\.|[^'\\\\])*'";
    private static final String NUMBER = "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[jJ]?\\b|0[xX][0-9a-fA-F]+";
    private static final String DECORATOR = "@[A-Za-z_]\\w*";

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(TRIPLE_DOUBLE, "syntax-string"),
                TokenGroup.of(TRIPLE_SINGLE, "syntax-string"),
                TokenGroup.of(COMMENT, "syntax-comment"),
                TokenGroup.of(PREFIX_STRING, "syntax-string"),
                TokenGroup.of(PREFIX_CHAR, "syntax-string"),
                TokenGroup.of(DECORATOR, "syntax-annotation"),
                TokenGroup.of(KEYWORDS, "syntax-keyword"),
                TokenGroup.of(NUMBER, "syntax-number")
        );
    }
}
