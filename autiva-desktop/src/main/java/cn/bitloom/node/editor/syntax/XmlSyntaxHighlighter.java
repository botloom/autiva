package cn.bitloom.node.editor.syntax;

import java.util.List;

/**
 * XML / HTML / FXML / SVG 语法高亮器。
 *
 * <p>识别注释、CDATA、标签名、属性名、属性值字符串、实体引用。
 * 适用于所有基于尖括号标签的标记语言。
 */
public final class XmlSyntaxHighlighter extends AbstractRegexHighlighter {

    private static final String COMMENT = "<!--[\\s\\S]*?-->";
    private static final String CDATA = "<!\\[CDATA\\[[\\s\\S]*?\\]\\]>";
    private static final String TAG = "</?[A-Za-z_][\\w:.-]*|/?>";
    private static final String ATTR = "\\b[A-Za-z_][\\w:.-]*(?=\\s*=)";
    private static final String STRING = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'";
    private static final String ENTITY = "&[A-Za-z#0-9]+;";

    @Override
    protected List<TokenGroup> getTokenGroups() {
        return List.of(
                TokenGroup.of(COMMENT, "syntax-comment"),
                TokenGroup.of(CDATA, "syntax-string"),
                TokenGroup.of(TAG, "syntax-keyword"),
                TokenGroup.of(ATTR, "syntax-annotation"),
                TokenGroup.of(STRING, "syntax-string"),
                TokenGroup.of(ENTITY, "syntax-literal")
        );
    }
}
