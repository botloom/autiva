package cn.bitloom.store;

import cn.bitloom.agentic.agent.ModelEnum;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Store {
    public static final StringProperty statusText = new SimpleStringProperty();
    public static final ObjectProperty<Path> browserPath = new SimpleObjectProperty<>(Paths.get("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));
    public static final ObjectProperty<ModelEnum> selectedModel = new SimpleObjectProperty<>(ModelEnum.DEEPSEEK);
}
