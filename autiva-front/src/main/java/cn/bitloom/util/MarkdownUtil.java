package cn.bitloom.util;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * The type Markdown util.
 */
public class MarkdownUtil {

    private static final Parser markdownParser = Parser.builder().build();
    private static final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    /**
     * Render markdown string.
     *
     * @param markdown the markdown
     * @return the string
     */
    public static String renderMarkdown(String markdown) {
        Node document = markdownParser.parse(markdown);
        return htmlRenderer.render(document);
    }

}
