package cn.bitloom.node.editor.syntax;

import java.util.List;

/**
 * Java 语法高亮器。
 *
 * <p>识别注释、字符串、字符、关键字、数字、注解、布尔/null 字面量。
 * 注释和字符串优先匹配以避免内部关键字被覆盖。
 */
public final class JavaSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String KEYWORDS =
            "\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|"
                    + "do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|"
                    + "instanceof|int|interface|long|native|new|package|private|protected|public|return|"
                    + "short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|"
                    + "void|volatile|while|var|yield|record|sealed|permits)\\b";

    private static final String LITERALS = "\\b(?:true|false|null)\\b";

    private static final String BLOCK_COMMENT = "/\\*[\\s\\S]*?\\*/";
    private static final String LINE_COMMENT = "//[^\\n]*";
    private static final String STRING = "\"(?:\\\\.|[^\"\\\\])*+\"";
    private static final String CHAR = "'(?:\\\\.|[^'\\\\])*+'";
    private static final String NUMBER = "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b";
    private static final String ANNOTATION = "@[A-Za-z_]\\w*";
    private static final String TYPE_NAME = "\\b[A-Z][A-Za-z0-9_]*\\b";

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(BLOCK_COMMENT, "syntax-comment"),
                TokenGroup.of(LINE_COMMENT, "syntax-comment"),
                TokenGroup.of(STRING, "syntax-string"),
                TokenGroup.of(CHAR, "syntax-string"),
                TokenGroup.of(ANNOTATION, "syntax-annotation"),
                TokenGroup.of(KEYWORDS, "syntax-keyword"),
                TokenGroup.of(LITERALS, "syntax-literal"),
                TokenGroup.of(NUMBER, "syntax-number"),
                TokenGroup.of(TYPE_NAME, "syntax-type")
        );
    }
}
