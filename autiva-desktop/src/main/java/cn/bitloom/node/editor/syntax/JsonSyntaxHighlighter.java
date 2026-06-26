package cn.bitloom.node.editor.syntax;

import java.util.List;

/**
 * JSON 语法高亮器。
 *
 * <p>识别对象键、字符串值、数字、布尔/null 字面量。
 * 键与字符串值的区分通过零宽断言 {@code (?=\\s*:)} 实现。
 */
public final class JsonSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String KEY = "\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)";
    private static final String STRING = "\"(?:\\\\.|[^\"\\\\])*\"";
    private static final String NUMBER = "-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b";
    private static final String LITERAL = "\\b(?:true|false|null)\\b";

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(KEY, "syntax-key"),
                TokenGroup.of(STRING, "syntax-string"),
                TokenGroup.of(LITERAL, "syntax-literal"),
                TokenGroup.of(NUMBER, "syntax-number")
        );
    }
}
