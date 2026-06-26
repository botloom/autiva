package cn.bitloom.node.editor.syntax;

import java.util.List;
import java.util.regex.Pattern;

/**
 * .properties 配置文件语法高亮器。
 *
 * <p>识别注释（# 或 ! 开头）、键（行首到 = 或 : 之前）、
 * 分隔符（= 或 :）、字符串值。续行（反斜杠换行）未处理。
 */
public final class PropertiesSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String COMMENT = "(?m)^[ \\t]*[#!][^\\n]*";
    private static final String KEY = "(?m)^[ \\t]*[^=#!:\\n][^=:#\\n]*?(?=\\s*[=:])";
    private static final String SEPARATOR = "[=:]";
    private static final String STRING = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'";

    @Override
    protected int getFlags() {
        return Pattern.MULTILINE;
    }

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(COMMENT, "syntax-comment"),
                TokenGroup.of(KEY, "syntax-key"),
                TokenGroup.of(SEPARATOR, "syntax-operator"),
                TokenGroup.of(STRING, "syntax-string")
        );
    }
}
