package cn.bitloom.controller;

import cn.bitloom.node.SvgImageView;
import cn.bitloom.util.MarkdownFxRenderer;
import cn.bitloom.util.SyntaxHighlighter;
import cn.bitloom.holder.DialogHolder;
import cn.bitloom.window.WindowChromeHelper;
import cn.bitloom.window.WindowManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileEditorController implements WindowManager.StageAware, DialogHolder {

    @FXML
    private BorderPane root;

    @FXML
    private HBox toolbar;

    @FXML
    private HBox windowControls;

    @FXML
    private Button minimizeBtn;

    @FXML
    private Button maximizeBtn;

    @FXML
    private Button closeBtn;

    @FXML
    private TreeView<Path> fileTree;

    @FXML
    private VBox treePanel;

    @FXML
    private SplitPane splitPane;

    @FXML
    private TabPane tabPane;

    @FXML
    private VBox emptyState;

    @FXML
    private VBox editorPanel;

    @FXML
    private ToggleButton treeToggleBtn;

    @FXML
    private Label filePathLabel;

    @FXML
    private Label encodingLabel;

    @FXML
    private Label lineColLabel;

    @FXML
    private Button previewBtn;

    @FXML
    private Button formatBtn;

    @Getter
    private Stage stage;

    private Path rootPath;
    private final Map<Path, OpenFile> openFiles = new LinkedHashMap<>();
    private final Map<Tab, OpenFile> tabToFile = new HashMap<>();

    private static final String ICON_BASE = "/cn/bitloom/images/";
    private final ExecutorService highlightExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "syntax-highlight");
        t.setDaemon(true);
        return t;
    });

    private static class OpenFile {
        Path path;
        Tab tab;
        StyleClassedTextArea codeEditor;
        VirtualizedScrollPane<StyleClassedTextArea> codeScrollPane;
        TextArea lineNumbers;
        boolean modified;
        FileType fileType;
        ScrollPane previewScrollPane;
        VBox previewContainer;
        boolean previewVisible;
        SplitPane editorSplitPane;
    }

    private enum FileType {
        MARKDOWN,
        CODE,
        TEXT
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> saveAllModifiedFiles());

        Platform.runLater(() -> {
            WindowChromeHelper.setup(stage, toolbar, root, minimizeBtn, maximizeBtn, closeBtn, 600, 400);
            WindowChromeHelper.setupClip(editorPanel, 8);
        });
    }

    @FXML
    public void initialize() {
        setupFileTree();
        setupTabPane();
        showEmptyState();
        filePathLabel.setText("就绪");
        encodingLabel.setText("UTF-8");
        lineColLabel.setText("行 1, 列 1");
    }

    public void initRootPath(Path rootPath) {
        this.rootPath = rootPath;
        loadFileTree();
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public StageStyle getStageStyle() {
        return StageStyle.TRANSPARENT;
    }

    private void setupFileTree() {
        fileTree.setCellFactory(param -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    setText(item.getFileName().toString());
                    boolean isDir = Files.isDirectory(item);
                    SvgImageView icon = createIcon(isDir ? "folder.svg" : "file.svg", 16, 16);
                    setGraphic(icon);
                    setContextMenu(createContextMenu(item, isDir));
                }
            }
        });

        fileTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Path> selected = fileTree.getSelectionModel().getSelectedItem();
                if (selected != null && !Files.isDirectory(selected.getValue())) {
                    openFile(selected.getValue());
                }
            }
        });
    }

    private void setupTabPane() {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                OpenFile file = tabToFile.get(newTab);
                if (file != null) {
                    updateStatusBar(file);
                    updateRightBarButtons(file.fileType);
                }
            }
        });

        tabPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.S) {
                saveCurrentFile();
                event.consume();
            }
        });

        tabPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                int currentIndex = tabPane.getSelectionModel().getSelectedIndex();
                int totalTabs = tabPane.getTabs().size();
                if (totalTabs <= 1) return;

                int newIndex;
                if (event.getDeltaY() > 0) {
                    newIndex = (currentIndex - 1 + totalTabs) % totalTabs;
                } else {
                    newIndex = (currentIndex + 1) % totalTabs;
                }
                tabPane.getSelectionModel().select(newIndex);
                event.consume();
            }
        });

        Platform.runLater(() -> {
            Node headerRegion = tabPane.lookup(".headers-region");
            if (headerRegion instanceof ScrollPane scrollPane) {
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            }
        });
    }

    private SvgImageView createIcon(String svgName, double width, double height) {
        SvgImageView view = new SvgImageView();
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setSvgPath(ICON_BASE + svgName);
        return view;
    }

    private ContextMenu createContextMenu(Path path, boolean isDirectory) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("file-editor__context-menu");

        boolean isRoot = path.equals(rootPath);

        MenuItem newFileItem = new MenuItem("新建文件");
        newFileItem.getStyleClass().add("file-editor__context-menu-item");
        newFileItem.setOnAction(evt -> createNewFile(path, isDirectory));
        menu.getItems().add(newFileItem);

        MenuItem newFolderItem = new MenuItem("新建文件夹");
        newFolderItem.getStyleClass().add("file-editor__context-menu-item");
        newFolderItem.setOnAction(evt -> createNewFolder(path, isDirectory));
        menu.getItems().add(newFolderItem);

        menu.getItems().add(new SeparatorMenuItem());

        MenuItem renameItem = new MenuItem("重命名");
        renameItem.getStyleClass().add("file-editor__context-menu-item");
        renameItem.setOnAction(evt -> renameItem(path));
        menu.getItems().add(renameItem);

        if (!isRoot) {
            MenuItem deleteItem = new MenuItem("删除");
            deleteItem.getStyleClass().add("file-editor__context-menu-item");
            deleteItem.setOnAction(evt -> deleteItem(path));
            menu.getItems().add(deleteItem);
        }

        return menu;
    }

    private boolean isBinaryFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".ico")
                || name.endsWith(".pdf") || name.endsWith(".zip") || name.endsWith(".jar")
                || name.endsWith(".class") || name.endsWith(".exe") || name.endsWith(".dll");
    }

    private FileType determineFileType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".md") || name.endsWith(".markdown")) return FileType.MARKDOWN;
        if (isCodeFile(name)) return FileType.CODE;
        return FileType.TEXT;
    }

    private boolean isCodeFile(String name) {
        return name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js")
                || name.endsWith(".ts") || name.endsWith(".json") || name.endsWith(".yaml")
                || name.endsWith(".yml") || name.endsWith(".xml") || name.endsWith(".html")
                || name.endsWith(".htm") || name.endsWith(".css") || name.endsWith(".sh")
                || name.endsWith(".bash") || name.endsWith(".sql") || name.endsWith(".properties")
                || name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h")
                || name.endsWith(".go") || name.endsWith(".rs") || name.endsWith(".rb")
                || name.endsWith(".php") || name.endsWith(".swift") || name.endsWith(".kt")
                || name.endsWith(".scala") || name.endsWith(".r") || name.endsWith(".lua")
                || name.endsWith(".pl") || name.endsWith(".groovy") || name.endsWith(".gradle")
                || name.endsWith(".toml") || name.endsWith(".ini") || name.endsWith(".cfg")
                || name.endsWith(".conf") || name.endsWith(".dockerfile") || name.endsWith(".makefile");
    }

    private void loadFileTree() {
        if (rootPath == null || !Files.exists(rootPath)) {
            return;
        }

        TreeItem<Path> rootItem = buildTree(rootPath);
        rootItem.setExpanded(true);
        fileTree.setRoot(rootItem);
        fileTree.setShowRoot(true);
    }

    private TreeItem<Path> buildTree(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);

        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                children.sorted(Comparator
                                .comparing((Path p) -> !Files.isDirectory(p))
                                .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                        .forEach(child -> {
                            TreeItem<Path> childItem = buildTree(child);
                            item.getChildren().add(childItem);
                        });
            } catch (IOException e) {
                log.error("Failed to list directory: {}", path, e);
            }
        }

        return item;
    }

    private void openFile(Path filePath) {
        if (openFiles.containsKey(filePath)) {
            OpenFile existing = openFiles.get(filePath);
            tabPane.getSelectionModel().select(existing.tab);
            updateRightBarButtons(existing.fileType);
            return;
        }

        if (isBinaryFile(filePath)) {
            filePathLabel.setText("不支持编辑此文件类型");
            return;
        }

        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            FileType fileType = determineFileType(filePath);

            OpenFile file = new OpenFile();
            file.path = filePath;
            file.modified = false;
            file.fileType = fileType;
            file.previewVisible = false;

            Tab tab = new Tab();
            tab.setClosable(true);
            tab.setText("");

            HBox tabGraphic = new HBox(6);
            tabGraphic.setAlignment(Pos.CENTER_LEFT);

            SvgImageView iconView = createIcon("file.svg", 14, 14);

            Label nameLabel = new Label(filePath.getFileName().toString());
            nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #cccccc;");
            nameLabel.getStyleClass().add("file-editor__tab-name");

            tabGraphic.getChildren().addAll(iconView, nameLabel);
            tab.setGraphic(tabGraphic);

            TextArea lineNumbers = createLineNumberArea();
            StyleClassedTextArea codeEditor = createCodeEditor(content, fileType, filePath.getFileName().toString());
            VirtualizedScrollPane<StyleClassedTextArea> codeScrollPane = new VirtualizedScrollPane<>(codeEditor);
            codeScrollPane.getStyleClass().add("file-editor__code-scroll");

            file.tab = tab;
            file.codeEditor = codeEditor;
            file.codeScrollPane = codeScrollPane;
            file.lineNumbers = lineNumbers;

            updateLineNumbers(lineNumbers, content);

            codeEditor.textProperty().addListener((obs, oldVal, newVal) -> {
                updateLineNumbers(lineNumbers, newVal);
                if (!file.modified) {
                    file.modified = true;
                    updateTabModifiedState(file);
                }
                updateLineColIndicator(codeEditor);
                if (fileType == FileType.MARKDOWN && file.previewVisible && file.previewContainer != null) {
                    updateMarkdownPreview(file.previewContainer, newVal);
                }
            });

            codeEditor.caretPositionProperty().addListener((obs, oldPos, newPos) ->
                    updateLineColIndicator(codeEditor));

            setupScrollSync(lineNumbers, codeEditor);

            Node editorContent;
            if (fileType == FileType.MARKDOWN) {
                VBox previewContainer = new VBox();
                previewContainer.getStyleClass().add("file-editor__preview");
                ScrollPane previewScrollPane = new ScrollPane(previewContainer);
                previewScrollPane.setFitToWidth(true);
                previewScrollPane.getStyleClass().add("file-editor__preview-scroll");
                file.previewContainer = previewContainer;
                file.previewScrollPane = previewScrollPane;

                SplitPane mdSplitPane = new SplitPane();
                mdSplitPane.getStyleClass().add("file-editor__md-split-pane");
                mdSplitPane.setDividerPositions(1.0);

                HBox editorBox = new HBox();
                editorBox.getStyleClass().add("file-editor__editor-box");
                editorBox.getChildren().addAll(lineNumbers, codeScrollPane);
                HBox.setHgrow(codeScrollPane, Priority.ALWAYS);

                mdSplitPane.getItems().addAll(editorBox, previewScrollPane);
                file.editorSplitPane = mdSplitPane;
                editorContent = mdSplitPane;
            } else {
                HBox editorBox = new HBox();
                editorBox.getStyleClass().add("file-editor__editor-box");
                editorBox.getChildren().addAll(lineNumbers, codeScrollPane);
                HBox.setHgrow(codeScrollPane, Priority.ALWAYS);
                editorContent = editorBox;
            }

            tab.setContent(editorContent);

            tab.setOnClosed(event -> {
                OpenFile closedFile = tabToFile.remove(tab);
                if (closedFile != null) {
                    if (closedFile.modified) {
                        saveFile(closedFile);
                    }
                    openFiles.remove(closedFile.path);
                }
                if (tabPane.getTabs().isEmpty()) {
                    showEmptyState();
                    updateRightBarButtons(null);
                }
            });

            tabPane.getTabs().add(tab);
            openFiles.put(filePath, file);
            tabToFile.put(tab, file);

            showEditorState();
            tabPane.getSelectionModel().select(tab);

            updateStatusBar(file);
            updateRightBarButtons(fileType);
        } catch (IOException e) {
            log.error("Failed to open file: {}", filePath, e);
            filePathLabel.setText("打开文件失败");
        }
    }

    private TextArea createLineNumberArea() {
        TextArea area = new TextArea("1");
        area.setEditable(false);
        area.setFocusTraversable(false);
        area.setMouseTransparent(true);
        area.setWrapText(false);
        area.setPrefWidth(55);
        area.setMinWidth(40);
        area.getStyleClass().add("file-editor__line-numbers");
        return area;
    }

    private StyleClassedTextArea createCodeEditor(String content, FileType fileType, String fileName) {
        StyleClassedTextArea area = new StyleClassedTextArea();
        area.setWrapText(false);
        area.getStyleClass().add("file-editor__code-editor");

        if (fileType == FileType.CODE) {
            area.getStyleClass().add("file-editor__code-editor--code");
        } else if (fileType == FileType.MARKDOWN) {
            area.getStyleClass().add("file-editor__code-editor--markdown");
        }

        var cssResource = getClass().getResource("/cn/bitloom/style/file-editor.css");
        if (cssResource == null) {
            log.error("Failed to load file-editor.css for syntax highlighting");
        } else {
            String cssPath = cssResource.toExternalForm();
            if (!area.getStylesheets().contains(cssPath)) {
                area.getStylesheets().add(cssPath);
            }
            log.debug("Loaded CSS for StyleClassedTextArea: {}", cssPath);
        }

        if (content != null && !content.isEmpty()) {
            StyleSpans<Collection<String>> spans = SyntaxHighlighter.computeHighlighting(fileName, content);
            area.insertText(0, content);
            area.setStyleSpans(0, spans);
        }

        area.textProperty().addListener((obs, oldVal, newVal) -> {
            highlightExecutor.submit(() -> {
                StyleSpans<Collection<String>> spans = SyntaxHighlighter.computeHighlighting(fileName, newVal);
                Platform.runLater(() -> {
                    if (area.getLength() == newVal.length()) {
                        area.setStyleSpans(0, spans);
                    }
                });
            });
        });

        return area;
    }

    private void setupScrollSync(TextArea lineNumbers, StyleClassedTextArea codeEditor) {
        Platform.runLater(() -> {
            ScrollPane lineScrollPane = (ScrollPane) lineNumbers.lookup(".scroll-pane");
            if (lineScrollPane == null) return;

            codeEditor.estimatedScrollYProperty().addListener((obs, oldVal, newVal) -> {
                double maxScroll = codeEditor.getTotalHeightEstimate() - codeEditor.getHeight();
                if (maxScroll <= 0) {
                    lineScrollPane.setVvalue(0);
                    return;
                }
                double ratio = newVal / maxScroll;
                lineScrollPane.setVvalue(ratio * (lineScrollPane.getVmax() - lineScrollPane.getVmin()) + lineScrollPane.getVmin());
            });
        });
    }

    private void updateLineNumbers(TextArea lineNumbers, String text) {
        int lineCount = text.isEmpty() ? 1 : text.split("\n", -1).length;
        int maxDigits = String.valueOf(lineCount).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineCount; i++) {
            if (i > 1) sb.append("\n");
            String num = String.valueOf(i);
            sb.append(" ".repeat(Math.max(0, maxDigits - num.length()))).append(num);
        }
        lineNumbers.setText(sb.toString());
        double width = Math.max(40, maxDigits * 10 + 28);
        lineNumbers.setPrefWidth(width);
    }

    private void updateTabModifiedState(OpenFile file) {
        HBox graphic = (HBox) file.tab.getGraphic();
        if (graphic == null) return;
        Label nameLabel = (Label) graphic.getChildren().get(1);
        if (file.modified) {
            nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e2b340;");
        } else {
            nameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #cccccc;");
        }
    }

    private void updateLineColIndicator(StyleClassedTextArea codeEditor) {
        String text = codeEditor.getText();
        int caretPos = codeEditor.getCaretPosition();
        int line = 1;
        int col = 1;
        for (int i = 0; i < caretPos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        lineColLabel.setText("行 " + line + ", 列 " + col);
    }

    private void updateStatusBar(OpenFile file) {
        try {
            filePathLabel.setText(rootPath.relativize(file.path).toString());
        } catch (Exception e) {
            filePathLabel.setText(file.path.getFileName().toString());
        }
        encodingLabel.setText("UTF-8");
        updateLineColIndicator(file.codeEditor);
    }

    private void saveFile(OpenFile file) {
        if (file.path == null || !file.modified) {
            return;
        }

        try {
            Files.writeString(file.path, file.codeEditor.getText(), StandardCharsets.UTF_8);
            file.modified = false;
            updateTabModifiedState(file);
            filePathLabel.setText("已保存: " + rootPath.relativize(file.path));
        } catch (IOException e) {
            log.error("Failed to save file: {}", file.path, e);
            filePathLabel.setText("保存失败");
        }
    }

    private void createNewFile(Path target, boolean isDirectory) {
        Path parentDir = isDirectory ? target : target.getParent();

        Optional<String> result = showInputDialog("新建文件", "untitled");
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }

        String fileName = result.get().trim();
        Path newFilePath = parentDir.resolve(fileName);

        if (Files.exists(newFilePath)) {
            filePathLabel.setText("文件已存在: " + fileName);
            return;
        }

        try {
            Files.writeString(newFilePath, "", StandardCharsets.UTF_8);
            refreshTree();
            openFile(newFilePath);
            filePathLabel.setText("已创建: " + fileName);
        } catch (IOException e) {
            log.error("Failed to create file: {}", newFilePath, e);
            filePathLabel.setText("创建文件失败");
        }
    }

    private void createNewFolder(Path target, boolean isDirectory) {
        Path parentDir = isDirectory ? target : target.getParent();

        Optional<String> result = showInputDialog("新建文件夹", "new-folder");
        if (result.isEmpty() || result.get().trim().isEmpty()) {
            return;
        }

        String folderName = result.get().trim();
        Path newFolderPath = parentDir.resolve(folderName);

        if (Files.exists(newFolderPath)) {
            filePathLabel.setText("文件夹已存在: " + folderName);
            return;
        }

        try {
            Files.createDirectories(newFolderPath);
            refreshTree();
            filePathLabel.setText("已创建文件夹: " + folderName);
        } catch (IOException e) {
            log.error("Failed to create folder: {}", newFolderPath, e);
            filePathLabel.setText("创建文件夹失败");
        }
    }

    private void renameItem(Path path) {
        String oldName = path.getFileName().toString();

        Optional<String> result = showInputDialog("重命名", oldName);
        if (result.isEmpty() || result.get().trim().isEmpty() || result.get().trim().equals(oldName)) {
            return;
        }

        String newName = result.get().trim();
        Path newPath = path.getParent().resolve(newName);

        if (Files.exists(newPath)) {
            filePathLabel.setText("名称已存在: " + newName);
            return;
        }

        try {
            Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING);

            OpenFile openFile = openFiles.get(path);
            if (openFile != null) {
                openFiles.remove(path);
                openFile.path = newPath;
                openFiles.put(newPath, openFile);

                HBox graphic = (HBox) openFile.tab.getGraphic();
                if (graphic != null) {
                    Label nameLabel = (Label) graphic.getChildren().get(1);
                    nameLabel.setText(newName);
                }
                updateStatusBar(openFile);
            }

            refreshTree();
            filePathLabel.setText("已重命名: " + oldName + " → " + newName);
        } catch (IOException e) {
            log.error("Failed to rename: {}", path, e);
            filePathLabel.setText("重命名失败");
        }
    }

    private void deleteItem(Path path) {
        boolean isRoot = path.equals(rootPath);
        if (isRoot) {
            return;
        }

        String itemName = path.getFileName().toString();
        Optional<Boolean> confirmed = showConfirmDialog("确认删除",
                "确定要删除 \"" + itemName + "\" 吗？此操作不可撤销。");
        if (confirmed.isEmpty() || !confirmed.get()) {
            return;
        }

        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.delete(path);
            }

            OpenFile openFile = openFiles.get(path);
            if (openFile != null) {
                tabPane.getTabs().remove(openFile.tab);
                openFiles.remove(path);
                tabToFile.remove(openFile.tab);
            } else {
                for (Map.Entry<Path, OpenFile> entry : new HashMap<>(openFiles).entrySet()) {
                    if (entry.getKey().startsWith(path)) {
                        tabPane.getTabs().remove(entry.getValue().tab);
                        openFiles.remove(entry.getKey());
                        tabToFile.remove(entry.getValue().tab);
                    }
                }
            }

            if (tabPane.getTabs().isEmpty()) {
                showEmptyState();
            }

            refreshTree();
            filePathLabel.setText("已删除: " + itemName);
        } catch (IOException e) {
            log.error("Failed to delete: {}", path, e);
            filePathLabel.setText("删除失败");
        }
    }

    @FXML
    private void handleNewFile() {
        TreeItem<Path> selected = fileTree.getSelectionModel().getSelectedItem();
        Path target = selected != null ? selected.getValue() : rootPath;
        boolean isDir = selected != null && Files.isDirectory(target);
        createNewFile(isDir ? target : target.getParent(), true);
    }

    @FXML
    private void handleNewFolder() {
        TreeItem<Path> selected = fileTree.getSelectionModel().getSelectedItem();
        Path target = selected != null ? selected.getValue() : rootPath;
        boolean isDir = selected != null && Files.isDirectory(target);
        createNewFolder(isDir ? target : target.getParent(), true);
    }

    private void saveCurrentFile() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return;
        OpenFile file = tabToFile.get(selectedTab);
        if (file != null) {
            saveFile(file);
        }
    }

    private void saveAllModifiedFiles() {
        for (OpenFile file : openFiles.values()) {
            if (file.modified) {
                saveFile(file);
            }
        }
    }

    private void updateRightBarButtons(FileType fileType) {
        if (fileType == FileType.MARKDOWN) {
            previewBtn.setDisable(false);
            previewBtn.setVisible(true);
        } else {
            previewBtn.setDisable(true);
            previewBtn.setVisible(false);
        }

        if (fileType == FileType.CODE || fileType == FileType.MARKDOWN) {
            formatBtn.setDisable(false);
            formatBtn.setVisible(true);
        } else {
            formatBtn.setDisable(true);
            formatBtn.setVisible(false);
        }
    }

    @FXML
    private void togglePreview() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return;
        OpenFile file = tabToFile.get(selectedTab);
        if (file == null || file.fileType != FileType.MARKDOWN) return;

        file.previewVisible = !file.previewVisible;

        if (file.editorSplitPane != null) {
            if (file.previewVisible) {
                if (!file.editorSplitPane.getItems().contains(file.previewScrollPane)) {
                    file.editorSplitPane.getItems().add(file.previewScrollPane);
                }
                file.editorSplitPane.setDividerPositions(0.5);
                updateMarkdownPreview(file.previewContainer, file.codeEditor.getText());
            } else {
                file.editorSplitPane.getItems().remove(file.previewScrollPane);
                file.editorSplitPane.setDividerPositions(1.0);
            }
        }
    }

    private void updateMarkdownPreview(VBox previewContainer, String markdownText) {
        previewContainer.getChildren().clear();
        try {
            javafx.scene.Node rendered = MarkdownFxRenderer.render(markdownText);
            rendered.getStyleClass().add("file-editor__preview-content");
            previewContainer.getChildren().add(rendered);
        } catch (Exception e) {
            log.error("Failed to render markdown preview", e);
            Label errorLabel = new Label("预览渲染失败");
            errorLabel.getStyleClass().add("file-editor__preview-error");
            previewContainer.getChildren().add(errorLabel);
        }
    }

    @FXML
    private void formatCode() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return;
        OpenFile file = tabToFile.get(selectedTab);
        if (file == null) return;

        String text = file.codeEditor.getText();
        String formatted = simpleFormat(text, file.fileType);
        if (!formatted.equals(text)) {
            StyleSpans<Collection<String>> spans = SyntaxHighlighter.computeHighlighting(
                    file.path.getFileName().toString(), formatted);
            file.codeEditor.clear();
            file.codeEditor.insertText(0, formatted);
            file.codeEditor.setStyleSpans(0, spans);
        }
    }

    private String simpleFormat(String text, FileType fileType) {
        if (fileType == FileType.CODE || fileType == FileType.MARKDOWN) {
            StringBuilder sb = new StringBuilder();
            String[] lines = text.split("\n");
            for (String line : lines) {
                sb.append(line.stripTrailing()).append("\n");
            }
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
                sb.setLength(sb.length() - 1);
            }
            return sb.toString();
        }
        return text;
    }

    @FXML
    private void toggleFileTree() {
        boolean show = treeToggleBtn.isSelected();
        if (show) {
            if (!splitPane.getItems().contains(treePanel)) {
                splitPane.getItems().add(0, treePanel);
                Platform.runLater(() -> splitPane.setDividerPositions(0.2));
            }
        } else {
            splitPane.getItems().remove(treePanel);
        }
    }

    private void refreshTree() {
        loadFileTree();
    }

    private void showEmptyState() {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        tabPane.setVisible(false);
        tabPane.setManaged(false);
    }

    private void showEditorState() {
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        tabPane.setVisible(true);
        tabPane.setManaged(true);
    }

    private Optional<String> showInputDialog(String title, String defaultValue) {
        final String[] result = {null};

        Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(stage);

        VBox dialogRoot = new VBox(16);
        dialogRoot.getStyleClass().add("file-editor__dialog");
        dialogRoot.setPadding(new Insets(24));
        dialogRoot.setPrefWidth(360);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("file-editor__dialog-title");

        TextField nameField = new TextField(defaultValue);
        nameField.getStyleClass().add("file-editor__dialog-input");

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("file-editor__dialog-btn");
        cancelBtn.setOnAction(e -> dialogStage.close());

        Button createBtn = new Button("创建");
        createBtn.getStyleClass().add("file-editor__dialog-btn--primary");
        createBtn.setDefaultButton(true);
        createBtn.setOnAction(e -> {
            result[0] = nameField.getText().trim();
            dialogStage.close();
        });

        buttons.getChildren().addAll(cancelBtn, createBtn);
        dialogRoot.getChildren().addAll(titleLabel, nameField, buttons);

        Scene scene = new Scene(dialogRoot);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/cn/bitloom/style/file-editor.css").toExternalForm());
        dialogStage.setScene(scene);

        WindowChromeHelper.setupDrag(dialogStage, dialogRoot);

        Platform.runLater(() -> {
            nameField.requestFocus();
            nameField.selectAll();
        });

        dialogStage.showAndWait();

        return Optional.ofNullable(result[0]);
    }

    private Optional<Boolean> showConfirmDialog(String title, String message) {
        final Boolean[] result = {null};

        Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(stage);

        VBox dialogRoot = new VBox(16);
        dialogRoot.getStyleClass().add("file-editor__dialog");
        dialogRoot.setPadding(new Insets(24));
        dialogRoot.setPrefWidth(360);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("file-editor__dialog-title");

        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("file-editor__dialog-message");
        messageLabel.setWrapText(true);

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("file-editor__dialog-btn");
        cancelBtn.setOnAction(e -> {
            result[0] = false;
            dialogStage.close();
        });

        Button confirmBtn = new Button("删除");
        confirmBtn.getStyleClass().add("file-editor__dialog-btn--danger");
        confirmBtn.setDefaultButton(true);
        confirmBtn.setOnAction(e -> {
            result[0] = true;
            dialogStage.close();
        });

        buttons.getChildren().addAll(cancelBtn, confirmBtn);
        dialogRoot.getChildren().addAll(titleLabel, messageLabel, buttons);

        Scene scene = new Scene(dialogRoot);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/cn/bitloom/style/file-editor.css").toExternalForm());
        dialogStage.setScene(scene);

        WindowChromeHelper.setupDrag(dialogStage, dialogRoot);

        dialogStage.showAndWait();

        return Optional.ofNullable(result[0]);
    }
}
