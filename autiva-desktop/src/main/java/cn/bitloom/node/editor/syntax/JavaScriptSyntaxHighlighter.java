package cn.bitloom.node.editor.syntax;

import java.util.List;

/**
 * JavaScript / TypeScript 语法高亮器。
 *
 * <p>覆盖 ECMAScript 关键字、字符串（含模板字符串）、注释、数字、
 * 装饰器（@decorator）。正则字面量因上下文敏感未识别。
 */
public final class JavaScriptSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String KEYWORDS =
            "\\b(?:break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|"
                    + "finally|for|function|if|import|in|instanceof|new|return|super|switch|this|throw|try|"
                    + "typeof|var|void|while|with|yield|let|static|async|await|"
                    // TypeScript 关键字
                    + "interface|type|enum|public|private|protected|readonly|abstract|as|namespace|declare|"
                    + "implements|from|of)\\b";

    private static final String LITERALS = "\\b(?:true|false|null|undefined|NaN|Infinity)\\b";

    private static final String BLOCK_COMMENT = "/\\*[\\s\\S]*?\\*/";
    private static final String LINE_COMMENT = "//[^\\n]*";
    private static final String DOUBLE_STRING = "\"(?:\\\\.|[^\"\\\\])*\"";
    private static final String SINGLE_STRING = "'(?:\\\\.|[^'\\\\])*'";
    private static final String TEMPLATE_STRING = "`(?:\\\\.|[^`\\\\])*`";
    private static final String NUMBER = "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?n?\\b|0[xX][0-9a-fA-F]+";
    private static final String DECORATOR = "@[A-Za-z_]\\w*";

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(BLOCK_COMMENT, "syntax-comment"),
                TokenGroup.of(LINE_COMMENT, "syntax-comment"),
                TokenGroup.of(DOUBLE_STRING, "syntax-string"),
                TokenGroup.of(SINGLE_STRING, "syntax-string"),
                TokenGroup.of(TEMPLATE_STRING, "syntax-string"),
                TokenGroup.of(DECORATOR, "syntax-annotation"),
                TokenGroup.of(KEYWORDS, "syntax-keyword"),
                TokenGroup.of(LITERALS, "syntax-literal"),
                TokenGroup.of(NUMBER, "syntax-number")
        );
    }
}
