package cn.bitloom.node.editor.syntax;

import java.util.List;
import java.util.regex.Pattern;

/**
 * YAML 语法高亮器。
 *
 * <p>识别注释、键（后跟冒号）、字符串、数字、布尔/null 字面量、
 * 文档分隔符（{@code ---}）、锚点（{@code &anchor}）和别名（{@code *alias}）。
 */
public final class YamlSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String COMMENT = "#[^\\n]*";
    private static final String DOC_SEP = "(?m)^-{3}(?=\\s|$)";
    private static final String KEY = "\\b[A-Za-z_][\\w-]*(?=\\s*:)";
    private static final String DOUBLE_STRING = "\"(?:\\\\.|[^\"\\\\])*\"";
    private static final String SINGLE_STRING = "'(?:\\\\.|[^'\\\\])*'";
    private static final String NUMBER = "-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b";
    private static final String LITERAL = "\\b(?:true|false|null|yes|no|on|off|True|False|Null|Yes|No|On|Off|TRUE|FALSE|NULL|YES|NO|ON|OFF)\\b";
    private static final String ANCHOR = "&[A-Za-z_]\\w*";
    private static final String ALIAS = "\\*[A-Za-z_]\\w*";

    @Override
    protected int getFlags() {
        return Pattern.MULTILINE;
    }

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(COMMENT, "syntax-comment"),
                TokenGroup.of(DOC_SEP, "syntax-annotation"),
                TokenGroup.of(DOUBLE_STRING, "syntax-string"),
                TokenGroup.of(SINGLE_STRING, "syntax-string"),
                TokenGroup.of(KEY, "syntax-key"),
                TokenGroup.of(LITERAL, "syntax-literal"),
                TokenGroup.of(NUMBER, "syntax-number"),
                TokenGroup.of(ANCHOR, "syntax-annotation"),
                TokenGroup.of(ALIAS, "syntax-type")
        );
    }
}
