package cn.bitloom.util;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    private static final Collection<String> SYNTAX_TEXT = Collections.singleton("syntax-text");

    private static final String[] JAVA_KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "var", "record", "sealed", "permits",
            "non-sealed", "yield"
    };

    private static final String[] PYTHON_KEYWORDS = {
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
            "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    };

    private static final String[] JS_KEYWORDS = {
            "async", "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof", "let",
            "new", "null", "of", "return", "static", "super", "switch", "this", "throw",
            "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield"
    };

    private static final String[] GO_KEYWORDS = {
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
            "map", "package", "range", "return", "select", "struct", "switch", "type", "var"
    };

    private static final String[] RUST_KEYWORDS = {
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
            "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
            "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
            "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while"
    };

    private static final String[] SQL_KEYWORDS = {
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "JOIN", "INNER",
            "LEFT", "RIGHT", "OUTER", "ON", "AND", "OR", "NOT", "NULL", "IS", "IN",
            "BETWEEN", "LIKE", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET",
            "UNION", "ALL", "AS", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX",
            "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END", "PRIMARY", "KEY",
            "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT", "CHECK", "UNIQUE"
    };

    private static final String[] SHELL_KEYWORDS = {
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
            "do", "done", "in", "function", "select", "time", "coproc", "return", "exit",
            "break", "continue", "declare", "export", "local", "readonly", "typeset",
            "source", "alias", "unalias", "set", "unset", "shift", "eval", "exec",
            "trap", "wait", "read", "echo", "printf", "true", "false", "test"
    };

    private static final String[] YAML_KEYWORDS = {
            "true", "false", "null", "yes", "no", "on", "off"
    };

    private static final String[] JSON_KEYWORDS = {
            "true", "false", "null"
    };

    private static final String[] MARKDOWN_KEYWORDS = {};

    private static final Pattern JAVA_PATTERN = buildPattern(JAVA_KEYWORDS, true);
    private static final Pattern PYTHON_PATTERN = buildPattern(PYTHON_KEYWORDS, true);
    private static final Pattern JS_PATTERN = buildPattern(JS_KEYWORDS, true);
    private static final Pattern GO_PATTERN = buildPattern(GO_KEYWORDS, true);
    private static final Pattern RUST_PATTERN = buildPattern(RUST_KEYWORDS, true);
    private static final Pattern SQL_PATTERN = buildPattern(SQL_KEYWORDS, false);
    private static final Pattern SHELL_PATTERN = buildPattern(SHELL_KEYWORDS, true);
    private static final Pattern YAML_PATTERN = buildPattern(YAML_KEYWORDS, false);
    private static final Pattern JSON_PATTERN = buildPattern(JSON_KEYWORDS, false);
    private static final Pattern MARKDOWN_PATTERN = buildMarkdownPattern();

    private static Pattern buildPattern(String[] keywords, boolean caseSensitive) {
        StringBuilder sb = new StringBuilder();

        sb.append("(?<TRIPLEDOUBLE>\"\"\"[\\s\\S]*?\"\"\")");
        sb.append("|(?<TRIPLESINGLE>'''[\\s\\S]*?''')");
        sb.append("|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\")");
        sb.append("|(?<SINGLEQUOTE>'(?:[^'\\\\]|\\\\.)*')");
        sb.append("|(?<COMMENT>//[^\n]*|/\\*(?:.|\\R)*?\\*/)");
        sb.append("|(?<HASHCOMMENT>#[^\n]*)");

        if (keywords.length > 0) {
            String keywordPattern = caseSensitive
                    ? "\\b(" + String.join("|", keywords) + ")\\b"
                    : "(?i)\\b(" + String.join("|", keywords) + ")\\b";
            sb.append("|(?<KEYWORD>").append(keywordPattern).append(")");
        }

        sb.append("|(?<NUMBER>\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b)");
        sb.append("|(?<ANNOTATION>@[\\w]+)");
        sb.append("|(?<BRACKET>[(){}\\[\\]])");
        sb.append("|(?<OPERATOR>[+\\-*/%=<>!&|^~?:]+)");
        sb.append("|(?<SEMICOLON>;)");

        return Pattern.compile(sb.toString());
    }

    private static Pattern buildMarkdownPattern() {
        StringBuilder sb = new StringBuilder();
        sb.append("(?<MDHEADER>^#{1,6}\\s+.*$)");
        sb.append("|(?<MDCODEBLOCK>```[\\s\\S]*?```)");
        sb.append("|(?<MDINLINECODE>`[^`]+`)");
        sb.append("|(?<MDBOLD>\\*\\*[^*]+\\*\\*)");
        sb.append("|(?<MDITALIC>\\*[^*]+\\*)");
        sb.append("|(?<MDLINK>\\[([^\\]]+)\\]\\(([^)]+)\\))");
        sb.append("|(?<MDLIST>^\\s*[-*+]\\s+.*$)");
        sb.append("|(?<MDNUMBERED>^\\s*\\d+\\.\\s+.*$)");
        sb.append("|(?<MDBLOCKQUOTE>^>\\s+.*$)");
        return Pattern.compile(sb.toString(), Pattern.MULTILINE);
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String fileName, String text) {
        String lowerName = fileName.toLowerCase();
        Pattern pattern;
        String type;

        if (lowerName.endsWith(".java")) {
            pattern = JAVA_PATTERN;
            type = "java";
        } else if (lowerName.endsWith(".py")) {
            pattern = PYTHON_PATTERN;
            type = "python";
        } else if (lowerName.endsWith(".js") || lowerName.endsWith(".ts")) {
            pattern = JS_PATTERN;
            type = "js";
        } else if (lowerName.endsWith(".go")) {
            pattern = GO_PATTERN;
            type = "go";
        } else if (lowerName.endsWith(".rs")) {
            pattern = RUST_PATTERN;
            type = "rust";
        } else if (lowerName.endsWith(".sql")) {
            pattern = SQL_PATTERN;
            type = "sql";
        } else if (lowerName.endsWith(".sh") || lowerName.endsWith(".bash")) {
            pattern = SHELL_PATTERN;
            type = "shell";
        } else if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
            pattern = YAML_PATTERN;
            type = "yaml";
        } else if (lowerName.endsWith(".json")) {
            pattern = JSON_PATTERN;
            type = "json";
        } else if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            pattern = MARKDOWN_PATTERN;
            type = "markdown";
        } else if (lowerName.endsWith(".xml") || lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            pattern = JAVA_PATTERN;
            type = "markup";
        } else {
            StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
            builder.add(SYNTAX_TEXT, text.length());
            return builder.create();
        }

        return computeHighlighting(pattern, text, type);
    }

    private static StyleSpans<Collection<String>> computeHighlighting(Pattern pattern, String text, String type) {
        if ("markdown".equals(type)) {
            return computeMarkdownHighlighting(pattern, text);
        }

        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = null;

            if (matcher.group("STRING") != null) {
                styleClass = "syntax-string";
            } else if (matcher.group("SINGLEQUOTE") != null) {
                styleClass = "syntax-string";
            } else if (matcher.group("TRIPLEDOUBLE") != null) {
                styleClass = "syntax-string";
            } else if (matcher.group("TRIPLESINGLE") != null) {
                styleClass = "syntax-string";
            } else if (matcher.group("COMMENT") != null) {
                styleClass = "syntax-comment";
            } else if (matcher.group("HASHCOMMENT") != null) {
                if ("python".equals(type) || "shell".equals(type) || "yaml".equals(type)) {
                    styleClass = "syntax-comment";
                }
            } else if (matcher.group("KEYWORD") != null) {
                styleClass = "syntax-keyword";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "syntax-number";
            } else if (matcher.group("ANNOTATION") != null) {
                styleClass = "syntax-annotation";
            } else if (matcher.group("BRACKET") != null) {
                styleClass = "syntax-bracket";
            } else if (matcher.group("OPERATOR") != null) {
                styleClass = "syntax-operator";
            } else if (matcher.group("SEMICOLON") != null) {
                styleClass = "syntax-semi";
            }

            if (styleClass != null) {
                spansBuilder.add(SYNTAX_TEXT, matcher.start() - lastKwEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastKwEnd = matcher.end();
            }
        }

        spansBuilder.add(SYNTAX_TEXT, text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private static StyleSpans<Collection<String>> computeMarkdownHighlighting(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = null;

            if (matcher.group("MDHEADER") != null) {
                styleClass = "syntax-md-header";
            } else if (matcher.group("MDCODEBLOCK") != null) {
                styleClass = "syntax-md-codeblock";
            } else if (matcher.group("MDINLINECODE") != null) {
                styleClass = "syntax-md-inlinecode";
            } else if (matcher.group("MDBOLD") != null) {
                styleClass = "syntax-md-bold";
            } else if (matcher.group("MDITALIC") != null) {
                styleClass = "syntax-md-italic";
            } else if (matcher.group("MDLINK") != null) {
                styleClass = "syntax-md-link";
            } else if (matcher.group("MDLIST") != null) {
                styleClass = "syntax-md-list";
            } else if (matcher.group("MDNUMBERED") != null) {
                styleClass = "syntax-md-list";
            } else if (matcher.group("MDBLOCKQUOTE") != null) {
                styleClass = "syntax-md-blockquote";
            }

            if (styleClass != null) {
                spansBuilder.add(SYNTAX_TEXT, matcher.start() - lastKwEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastKwEnd = matcher.end();
            }
        }

        spansBuilder.add(SYNTAX_TEXT, text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
