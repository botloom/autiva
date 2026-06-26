package cn.bitloom.node.editor.syntax;

import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于正则的语法高亮器抽象基类。
 *
 * <p>子类通过 {@link #getTokenGroups()} 提供若干 {@link TokenGroup}，
 * 每组包含一段正则和对应的 CSS 样式类名。基类将这些正则按顺序
 * 用 {@code |} 拼接成一个 alternation 模式，匹配时按组序号
 * 判断命中位置，并应用对应样式类。
 *
 * <p>alternation 模式中靠前的组优先匹配，因此子类应将
 * 注释、字符串等"宽匹配"规则放在前面，关键字、数字等
 * "窄匹配"规则放在后面，避免注释内的关键字被覆盖。
 *
 * <p>正则中如需使用分组，必须使用非捕获分组 {@code (?:...)}，
 * 否则会破坏基类的捕获组序号映射。
 */
public abstract class AbstractRegexHighlighter implements SyntaxHighlighter {

    private Pattern cachedPattern;
    private List<String> cachedStyleClasses;

    /**
     * 提供高亮规则列表。
     *
     * <p>该方法的返回值在第一次 {@link #apply} 调用后被缓存，
     * 后续调用不会再次执行。子类应保证返回值在多次调用间稳定。
     */
    protected abstract List<TokenGroup> getTokenGroups();

    /**
     * 正则编译 flags，默认 0。子类可重写以启用 MULTILINE / CASE_INSENSITIVE 等。
     */
    protected int getFlags() {
        return 0;
    }

    @Override
    public void apply(StyleClassedTextArea area, String text) {
        // 优先使用 area 内部文本作为匹配源，确保字符偏移与文档一致。
        // 若直接用传入的 text（可能含 \r\n），而 CodeArea 内部已规范化行尾，
        // 会导致偏移量错位，setStyleClass 抛出 IndexOutOfBoundsException。
        String source = area.getText();
        if (source == null || source.isEmpty()) {
            source = text;
        }
        if (source == null || source.isEmpty()) {
            return;
        }
        int docLength = area.getLength();
        Pattern pattern = getOrCompilePattern();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            for (int i = 0; i < cachedStyleClasses.size(); i++) {
                int start = matcher.start(i + 1);
                int end = matcher.end(i + 1);
                if (start >= 0 && end > start && end <= docLength) {
                    area.setStyleClass(start, end, cachedStyleClasses.get(i));
                    break;
                }
            }
        }
    }

    private Pattern getOrCompilePattern() {
        if (cachedPattern == null) {
            List<TokenGroup> groups = getTokenGroups();
            StringBuilder patternBuilder = new StringBuilder();
            cachedStyleClasses = new ArrayList<>(groups.size());
            for (int i = 0; i < groups.size(); i++) {
                TokenGroup g = groups.get(i);
                if (i > 0) {
                    patternBuilder.append('|');
                }
                patternBuilder.append('(').append(g.regex()).append(')');
                cachedStyleClasses.add(g.styleClass());
            }
            cachedPattern = Pattern.compile(patternBuilder.toString(), getFlags());
        }
        return cachedPattern;
    }

    /**
     * 一条高亮规则：正则片段 + 样式类名。
     *
     * <p>正则片段中只允许使用非捕获分组 {@code (?:...)}，
     * 基类会自动包一层捕获组用于命中判断。
     */
    public record TokenGroup(String regex, String styleClass) {
        public static TokenGroup of(String regex, String styleClass) {
            return new TokenGroup(regex, styleClass);
        }
    }
}
