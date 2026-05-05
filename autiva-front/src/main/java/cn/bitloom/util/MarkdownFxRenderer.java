package cn.bitloom.util;

import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MarkdownFxRenderer {

    private static final String FONT_FAMILY = "\"SF Pro Text\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif";
    private static final String CODE_FONT_FAMILY = "\"SF Mono\", Monaco, \"Cascadia Code\", monospace";
    private static final double BASE_FONT_SIZE = 15;
    private static final double CODE_FONT_SIZE = 13;

    public static VBox render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new VBox();
        }
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder()
            .extensions(java.util.List.of(
                org.commonmark.ext.gfm.tables.TablesExtension.create()
            ))
            .build();
        org.commonmark.node.Node document = parser.parse(markdown);
        VBox container = new VBox(8);
        container.getStyleClass().add("md-content");
        container.setMaxWidth(Double.MAX_VALUE);
        container.setFillWidth(true);
        org.commonmark.node.Node child = document.getFirstChild();
        while (child != null) {
            Node fxNode = renderBlock(child);
            if (fxNode != null) {
                container.getChildren().add(fxNode);
            }
            child = child.getNext();
        }
        return container;
    }

    private static Node renderBlock(org.commonmark.node.Node block) {
        if (block instanceof Paragraph) return renderParagraph((Paragraph) block);
        if (block instanceof Heading) return renderHeading((Heading) block);
        if (block instanceof FencedCodeBlock) return renderFencedCodeBlock((FencedCodeBlock) block);
        if (block instanceof IndentedCodeBlock) return renderIndentedCodeBlock((IndentedCodeBlock) block);
        if (block instanceof BulletList) return renderBulletList((BulletList) block);
        if (block instanceof OrderedList) return renderOrderedList((OrderedList) block);
        if (block instanceof BlockQuote) return renderBlockQuote((BlockQuote) block);
        if (block instanceof ThematicBreak) return renderThematicBreak();
        if (block instanceof HtmlBlock) return renderHtmlBlock((HtmlBlock) block);
        if (block instanceof org.commonmark.ext.gfm.tables.TableBlock) return renderTable((org.commonmark.ext.gfm.tables.TableBlock) block);
        return null;
    }

    private static Node renderParagraph(Paragraph paragraph) {
        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("md-paragraph");
        textFlow.setMaxWidth(Double.MAX_VALUE);
        renderInlines(paragraph, textFlow, FontWeight.NORMAL, BASE_FONT_SIZE);
        return textFlow;
    }

    private static Node renderHeading(Heading heading) {
        TextFlow textFlow = new TextFlow();
        double fontSize = switch (heading.getLevel()) {
            case 1 -> 24;
            case 2 -> 20;
            case 3 -> 18;
            case 4 -> 16;
            case 5 -> 15;
            default -> 14;
        };
        textFlow.getStyleClass().add("md-heading");
        textFlow.getStyleClass().add("md-heading-" + heading.getLevel());
        textFlow.setMaxWidth(Double.MAX_VALUE);
        renderInlines(heading, textFlow, FontWeight.BOLD, fontSize);
        return textFlow;
    }

    private static Node renderFencedCodeBlock(FencedCodeBlock codeBlock) {
        VBox container = new VBox();
        container.getStyleClass().add("md-code-block");

        String info = codeBlock.getInfo();
        HBox header = new HBox();
        header.getStyleClass().add("md-code-header");
        if (info != null && !info.isBlank()) {
            Label langLabel = new Label(info);
            langLabel.getStyleClass().add("md-code-lang");
            header.getChildren().add(langLabel);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().add(spacer);

        Label copyBtn = new Label("复制");
        copyBtn.getStyleClass().add("md-code-copy");
        String code = codeBlock.getLiteral();
        copyBtn.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(code);
            clipboard.setContent(content);
            copyBtn.setText("已复制");
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(evt -> copyBtn.setText("复制"));
            pause.play();
        });
        header.getChildren().add(copyBtn);
        container.getChildren().add(header);

        TextFlow codeFlow = new TextFlow();
        codeFlow.getStyleClass().add("md-code-content");
        
        List<Text> highlightedTexts = createHighlightedText(code, info);
        codeFlow.getChildren().addAll(highlightedTexts);
        container.getChildren().add(codeFlow);

        return container;
    }

    private static Node renderIndentedCodeBlock(IndentedCodeBlock codeBlock) {
        VBox container = new VBox();
        container.getStyleClass().add("md-code-block");

        TextFlow codeFlow = new TextFlow();
        codeFlow.getStyleClass().add("md-code-content");
        
        List<Text> highlightedTexts = createHighlightedText(codeBlock.getLiteral(), null);
        codeFlow.getChildren().addAll(highlightedTexts);
        container.getChildren().add(codeFlow);

        return container;
    }

    private static Node renderBulletList(BulletList list) {
        VBox container = new VBox(2);
        container.getStyleClass().add("md-list");
        org.commonmark.node.Node child = list.getFirstChild();
        while (child != null) {
            if (child instanceof ListItem) {
                container.getChildren().add(renderListItem((ListItem) child, "•"));
            }
            child = child.getNext();
        }
        return container;
    }

    private static Node renderOrderedList(OrderedList list) {
        VBox container = new VBox(2);
        container.getStyleClass().add("md-list");
        int index = list.getStartNumber();
        org.commonmark.node.Node child = list.getFirstChild();
        while (child != null) {
            if (child instanceof ListItem) {
                container.getChildren().add(renderListItem((ListItem) child, index + "."));
                index++;
            }
            child = child.getNext();
        }
        return container;
    }

    private static Node renderListItem(ListItem item, String marker) {
        HBox hbox = new HBox(4);
        hbox.getStyleClass().add("md-list-item");
        Text markerText = new Text(marker);
        markerText.setFont(Font.font(FONT_FAMILY, BASE_FONT_SIZE));
        markerText.getStyleClass().add("md-list-marker");
        hbox.getChildren().add(markerText);

        VBox content = new VBox(2);
        org.commonmark.node.Node child = item.getFirstChild();
        while (child != null) {
            Node fxNode = renderBlock(child);
            if (fxNode != null) {
                content.getChildren().add(fxNode);
            }
            child = child.getNext();
        }
        hbox.getChildren().add(content);
        return hbox;
    }

    private static Node renderBlockQuote(BlockQuote quote) {
        HBox hbox = new HBox();
        hbox.getStyleClass().add("md-blockquote");

        Region leftBar = new Region();
        leftBar.getStyleClass().add("md-blockquote-bar");
        leftBar.setPrefWidth(3);
        leftBar.setMinWidth(3);
        hbox.getChildren().add(leftBar);

        VBox content = new VBox(4);
        content.getStyleClass().add("md-blockquote-content");
        org.commonmark.node.Node child = quote.getFirstChild();
        while (child != null) {
            Node fxNode = renderBlock(child);
            if (fxNode != null) {
                content.getChildren().add(fxNode);
            }
            child = child.getNext();
        }
        hbox.getChildren().add(content);
        return hbox;
    }

    private static Node renderThematicBreak() {
        Separator separator = new Separator();
        separator.getStyleClass().add("md-thematic-break");
        return separator;
    }

    private static Node renderHtmlBlock(HtmlBlock htmlBlock) {
        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("md-html-block");
        Text text = new Text(htmlBlock.getLiteral());
        text.setFont(Font.font(CODE_FONT_FAMILY, 13));
        textFlow.getChildren().add(text);
        return textFlow;
    }

    private static void renderInlines(org.commonmark.node.Node parent, TextFlow textFlow, FontWeight fontWeight, double fontSize) {
        org.commonmark.node.Node child = parent.getFirstChild();
        while (child != null) {
            renderInline(child, textFlow, fontWeight, fontSize);
            child = child.getNext();
        }
    }

    private static void renderInline(org.commonmark.node.Node inline, TextFlow textFlow, FontWeight fontWeight, double fontSize) {
        if (inline instanceof org.commonmark.node.Text textNode) {
            Text text = new Text(textNode.getLiteral());
            text.setFont(Font.font(FONT_FAMILY, fontWeight, FontPosture.REGULAR, fontSize));
            textFlow.getChildren().add(text);
        } else if (inline instanceof StrongEmphasis) {
            renderInlines(inline, textFlow, FontWeight.BOLD, fontSize);
        } else if (inline instanceof Emphasis) {
            org.commonmark.node.Node emphasisChild = inline.getFirstChild();
            while (emphasisChild != null) {
                if (emphasisChild instanceof org.commonmark.node.Text textNode) {
                    Text text = new Text(textNode.getLiteral());
                    text.setFont(Font.font(FONT_FAMILY, fontWeight, FontPosture.ITALIC, fontSize));
                    textFlow.getChildren().add(text);
                } else {
                    renderInline(emphasisChild, textFlow, fontWeight, fontSize);
                }
                emphasisChild = emphasisChild.getNext();
            }
        } else if (inline instanceof Code codeNode) {
            Text text = new Text(codeNode.getLiteral());
            text.setFont(Font.font(CODE_FONT_FAMILY, 13));
            text.getStyleClass().add("md-inline-code");
            textFlow.getChildren().add(text);
        } else if (inline instanceof Link link) {
            Hyperlink hyperlink = new Hyperlink();
            hyperlink.getStyleClass().add("md-link");
            hyperlink.setFocusTraversable(false);
            hyperlink.setText(extractNodeText(link));
            String dest = link.getDestination();
            hyperlink.setOnAction(e -> {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(dest));
                    }
                } catch (Exception ex) {
                    log.error("Failed to open link: {}", dest, ex);
                }
            });
            textFlow.getChildren().add(hyperlink);
        } else if (inline instanceof Image img) {
            Text altText = new Text(img.getTitle() != null ? img.getTitle() : "🖼");
            altText.setFont(Font.font(FONT_FAMILY, fontWeight, fontSize));
            textFlow.getChildren().add(altText);
        } else if (inline instanceof SoftLineBreak) {
            textFlow.getChildren().add(new Text("\n"));
        } else if (inline instanceof HardLineBreak) {
            textFlow.getChildren().add(new Text("\n"));
        } else if (inline instanceof HtmlInline htmlInline) {
            Text text = new Text(htmlInline.getLiteral());
            text.setFont(Font.font(CODE_FONT_FAMILY, 12));
            textFlow.getChildren().add(text);
        }
    }

    private static Node renderTable(org.commonmark.ext.gfm.tables.TableBlock table) {
        VBox container = new VBox();
        container.getStyleClass().add("md-table");
        
        org.commonmark.node.Node child = table.getFirstChild();
        org.commonmark.ext.gfm.tables.TableHead head = null;
        org.commonmark.ext.gfm.tables.TableBody body = null;
        
        while (child != null) {
            if (child instanceof org.commonmark.ext.gfm.tables.TableHead) {
                head = (org.commonmark.ext.gfm.tables.TableHead) child;
            } else if (child instanceof org.commonmark.ext.gfm.tables.TableBody) {
                body = (org.commonmark.ext.gfm.tables.TableBody) child;
            }
            child = child.getNext();
        }
        
        List<Double> columnWidths = new ArrayList<>();
        
        if (head != null) {
            org.commonmark.node.Node rowNode = head.getFirstChild();
            if (rowNode instanceof org.commonmark.ext.gfm.tables.TableRow) {
                org.commonmark.ext.gfm.tables.TableRow headerTableRow = (org.commonmark.ext.gfm.tables.TableRow) rowNode;
                org.commonmark.node.Node cell = headerTableRow.getFirstChild();
                while (cell != null) {
                    if (cell instanceof org.commonmark.ext.gfm.tables.TableCell) {
                        String headerText = extractText((org.commonmark.ext.gfm.tables.TableCell) cell);
                        double width = Math.max(150, headerText.length() * 10.0 + 32);
                        columnWidths.add(width);
                    }
                    cell = cell.getNext();
                }
            }
        }
        
        if (body != null) {
            org.commonmark.node.Node rowNode = body.getFirstChild();
            while (rowNode != null) {
                if (rowNode instanceof org.commonmark.ext.gfm.tables.TableRow) {
                    org.commonmark.ext.gfm.tables.TableRow row = (org.commonmark.ext.gfm.tables.TableRow) rowNode;
                    org.commonmark.node.Node cell = row.getFirstChild();
                    int colIndex = 0;
                    while (cell != null) {
                        if (cell instanceof org.commonmark.ext.gfm.tables.TableCell) {
                            String cellText = extractText((org.commonmark.ext.gfm.tables.TableCell) cell);
                            double width = Math.max(150, cellText.length() * 8.0 + 32);
                            if (colIndex < columnWidths.size()) {
                                columnWidths.set(colIndex, Math.max(columnWidths.get(colIndex), width));
                            } else {
                                columnWidths.add(width);
                            }
                            colIndex++;
                        }
                        cell = cell.getNext();
                    }
                }
                rowNode = rowNode.getNext();
            }
        }
        
        if (head != null) {
            HBox headerRow = new HBox();
            headerRow.getStyleClass().add("md-table-header-row");
            org.commonmark.node.Node rowNode = head.getFirstChild();
            if (rowNode instanceof org.commonmark.ext.gfm.tables.TableRow) {
                org.commonmark.ext.gfm.tables.TableRow headerTableRow = (org.commonmark.ext.gfm.tables.TableRow) rowNode;
                org.commonmark.node.Node cell = headerTableRow.getFirstChild();
                int colIndex = 0;
                while (cell != null) {
                    if (cell instanceof org.commonmark.ext.gfm.tables.TableCell) {
                        String headerText = extractText((org.commonmark.ext.gfm.tables.TableCell) cell);
                        Label label = new Label(headerText);
                        label.getStyleClass().add("md-table-header-cell");
                        double width = colIndex < columnWidths.size() ? columnWidths.get(colIndex) : 150;
                        label.setMinWidth(width);
                        label.setPrefWidth(width);
                        label.setMaxWidth(width);
                        headerRow.getChildren().add(label);
                        colIndex++;
                    }
                    cell = cell.getNext();
                }
            }
            container.getChildren().add(headerRow);
        }
        
        if (body != null) {
            org.commonmark.node.Node rowNode = body.getFirstChild();
            List<HBox> rows = new ArrayList<>();
            while (rowNode != null) {
                if (rowNode instanceof org.commonmark.ext.gfm.tables.TableRow) {
                    org.commonmark.ext.gfm.tables.TableRow row = (org.commonmark.ext.gfm.tables.TableRow) rowNode;
                    HBox dataRow = new HBox();
                    dataRow.getStyleClass().add("md-table-row");
                    if (rows.size() % 2 == 1) {
                        dataRow.getStyleClass().add("md-table-row-odd");
                    }
                    org.commonmark.node.Node cell = row.getFirstChild();
                    int colIndex = 0;
                    while (cell != null) {
                        if (cell instanceof org.commonmark.ext.gfm.tables.TableCell) {
                            String cellText = extractText((org.commonmark.ext.gfm.tables.TableCell) cell);
                            Label label = new Label(cellText);
                            label.getStyleClass().add("md-table-cell");
                            double width = colIndex < columnWidths.size() ? columnWidths.get(colIndex) : 150;
                            label.setMinWidth(width);
                            label.setPrefWidth(width);
                            label.setMaxWidth(width);
                            label.setWrapText(true);
                            dataRow.getChildren().add(label);
                            colIndex++;
                        }
                        cell = cell.getNext();
                    }
                    rows.add(dataRow);
                }
                rowNode = rowNode.getNext();
            }
            
            for (int i = 0; i < rows.size(); i++) {
                HBox dataRow = rows.get(i);
                if (i == rows.size() - 1) {
                    dataRow.getStyleClass().add("md-table-row-last");
                }
                container.getChildren().add(dataRow);
            }
        }
        
        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.getStyleClass().add("md-table-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMaxWidth(Double.MAX_VALUE);
        
        return scrollPane;
    }
    
    private static String extractText(org.commonmark.ext.gfm.tables.TableCell cell) {
        StringBuilder sb = new StringBuilder();
        org.commonmark.node.Node child = cell.getFirstChild();
        while (child != null) {
            if (child instanceof org.commonmark.node.Text) {
                sb.append(((org.commonmark.node.Text) child).getLiteral());
            } else if (child instanceof org.commonmark.node.Code) {
                sb.append(((org.commonmark.node.Code) child).getLiteral());
            } else if (child instanceof org.commonmark.node.StrongEmphasis || child instanceof org.commonmark.node.Emphasis) {
                org.commonmark.node.Node emphChild = child.getFirstChild();
                while (emphChild != null) {
                    if (emphChild instanceof org.commonmark.node.Text) {
                        sb.append(((org.commonmark.node.Text) emphChild).getLiteral());
                    }
                    emphChild = emphChild.getNext();
                }
            }
            child = child.getNext();
        }
        return sb.toString();
    }

    private static String extractNodeText(org.commonmark.node.Node node) {
        StringBuilder sb = new StringBuilder();
        org.commonmark.node.Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof org.commonmark.node.Text textNode) {
                sb.append(textNode.getLiteral());
            } else if (child instanceof Code code) {
                sb.append(code.getLiteral());
            } else if (child instanceof Emphasis || child instanceof StrongEmphasis) {
                sb.append(extractNodeText(child));
            }
            child = child.getNext();
        }
        return sb.toString();
    }
    
    private static List<Text> createHighlightedText(String code, String language) {
        List<Text> texts = new ArrayList<>();
        
        if (language == null || language.isBlank()) {
            Text text = new Text(code);
            text.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
            text.getStyleClass().add("md-code-text");
            texts.add(text);
            return texts;
        }
        
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                texts.add(new Text("\n"));
            }
            String line = lines[i];
            List<Text> lineTexts = highlightLine(line, language.toLowerCase());
            texts.addAll(lineTexts);
        }
        
        return texts;
    }
    
    private static List<Text> highlightLine(String line, String language) {
        List<Text> texts = new ArrayList<>();
        
        if (line.isEmpty()) {
            return texts;
        }
        
        switch (language) {
            case "java":
                texts.addAll(highlightJavaLine(line));
                break;
            case "python":
            case "py":
                texts.addAll(highlightPythonLine(line));
                break;
            case "javascript":
            case "js":
            case "typescript":
            case "ts":
                texts.addAll(highlightJSLine(line));
                break;
            default:
                Text text = new Text(line);
                text.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
                text.getStyleClass().add("md-code-text");
                texts.add(text);
        }
        
        return texts;
    }
    
    private static List<Text> highlightJavaLine(String line) {
        List<Text> texts = new ArrayList<>();
        String[] keywords = {"public", "private", "protected", "class", "interface", "extends", "implements",
            "void", "int", "long", "double", "float", "boolean", "char", "String", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "new", "this", "super", "static",
            "final", "abstract", "try", "catch", "finally", "throw", "throws", "import", "package", "null",
            "true", "false", "instanceof"};
        
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                if (current.length() > 0) {
                    texts.addAll(parseAndHighlight(current.toString(), keywords, "java"));
                    current = new StringBuilder();
                }
                Text comment = new Text(line.substring(i));
                comment.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
                comment.getStyleClass().add("md-code-comment");
                texts.add(comment);
                return texts;
            }
            
            if (c == '"') {
                if (current.length() > 0) {
                    texts.addAll(parseAndHighlight(current.toString(), keywords, "java"));
                    current = new StringBuilder();
                }
                int end = line.indexOf('"', i + 1);
                if (end == -1) end = line.length() - 1;
                Text string = new Text(line.substring(i, end + 1));
                string.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
                string.getStyleClass().add("md-code-string");
                texts.add(string);
                i = end;
                continue;
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            texts.addAll(parseAndHighlight(current.toString(), keywords, "java"));
        }
        
        return texts;
    }
    
    private static List<Text> highlightPythonLine(String line) {
        List<Text> texts = new ArrayList<>();
        String[] keywords = {"def", "class", "if", "elif", "else", "for", "while", "try", "except",
            "finally", "with", "as", "import", "from", "return", "yield", "raise", "pass", "break",
            "continue", "and", "or", "not", "in", "is", "lambda", "True", "False", "None", "self"};
        
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '#') {
                if (current.length() > 0) {
                    texts.addAll(parseAndHighlight(current.toString(), keywords, "python"));
                    current = new StringBuilder();
                }
                Text comment = new Text(line.substring(i));
                comment.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
                comment.getStyleClass().add("md-code-comment");
                texts.add(comment);
                return texts;
            }
            
            if (c == '"' || c == '\'') {
                if (current.length() > 0) {
                    texts.addAll(parseAndHighlight(current.toString(), keywords, "python"));
                    current = new StringBuilder();
                }
                int end = line.indexOf(c, i + 1);
                if (end == -1) end = line.length() - 1;
                Text string = new Text(line.substring(i, end + 1));
                string.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
                string.getStyleClass().add("md-code-string");
                texts.add(string);
                i = end;
                continue;
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            texts.addAll(parseAndHighlight(current.toString(), keywords, "python"));
        }
        
        return texts;
    }
    
    private static List<Text> highlightJSLine(String line) {
        List<Text> texts = new ArrayList<>();
        String[] keywords = {"function", "const", "let", "var", "if", "else", "for", "while", "do",
            "switch", "case", "break", "continue", "return", "class", "extends", "new", "this", "super",
            "import", "export", "from", "async", "await", "try", "catch", "finally", "throw", "typeof",
            "instanceof", "null", "undefined", "true", "false"};
        
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                if (current.length() > 0) {
                    texts.addAll(parseAndHighlight(current.toString(), keywords, "js"));
                    current = new StringBuilder();
                }
                Text comment = new Text(line.substring(i));
                comment.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
                comment.getStyleClass().add("md-code-comment");
                texts.add(comment);
                return texts;
            }
            
            if (c == '"' || c == '\'' || c == '`') {
                if (current.length() > 0) {
                    texts.addAll(parseAndHighlight(current.toString(), keywords, "js"));
                    current = new StringBuilder();
                }
                int end = line.indexOf(c, i + 1);
                if (end == -1) end = line.length() - 1;
                Text string = new Text(line.substring(i, end + 1));
                string.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
                string.getStyleClass().add("md-code-string");
                texts.add(string);
                i = end;
                continue;
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            texts.addAll(parseAndHighlight(current.toString(), keywords, "js"));
        }
        
        return texts;
    }
    
    private static List<Text> parseAndHighlight(String text, String[] keywords, String language) {
        List<Text> texts = new ArrayList<>();
        String[] words = text.split("(?<=[\\s\\[\\]{}(),;.=+\\-*/<>!&|])|(?=[\\s\\[\\]{}(),;.=+\\-*/<>!&|])");
        
        for (String word : words) {
            if (word.isEmpty()) continue;
            
            Text t = new Text(word);
            t.setFont(Font.font(CODE_FONT_FAMILY, CODE_FONT_SIZE));
            
            boolean isKeyword = false;
            for (String keyword : keywords) {
                if (word.equals(keyword)) {
                    isKeyword = true;
                    break;
                }
            }
            
            if (isKeyword) {
                t.getStyleClass().add("md-code-keyword");
            } else if (word.matches("\\d+(\\.\\d+)?")) {
                t.getStyleClass().add("md-code-number");
            } else {
                t.getStyleClass().add("md-code-text");
            }
            
            texts.add(t);
        }
        
        return texts;
    }
}
