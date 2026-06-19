package cn.bitloom.store;

import cn.bitloom.agentic.model.ModelTypeEnum;
import cn.bitloom.pet.PetType;
import javafx.beans.property.*;

public class Store {
    public static final StringProperty source = new SimpleStringProperty("desktopApp");

    public static final StringProperty userId = new SimpleStringProperty("default");

    public static final StringProperty statusText = new SimpleStringProperty();

    public static final ObjectProperty<ModelTypeEnum> selectedModel = new SimpleObjectProperty<>(ModelTypeEnum.DEEPSEEK);

    public static final StringProperty currentSessionId = new SimpleStringProperty();

    public static final ObjectProperty<String> currentAgent = new SimpleObjectProperty<>("default");

    public static final BooleanProperty isStreaming = new SimpleBooleanProperty(false);

    public static final BooleanProperty isPaused = new SimpleBooleanProperty(false);

    public static final StringProperty currentRoute = new SimpleStringProperty();

    public static final BooleanProperty petVisible = new SimpleBooleanProperty(false);

    public static final ObjectProperty<PetType> currentPetType = new SimpleObjectProperty<>(PetType.SUNFLOWER);

    public static final DoubleProperty growthProgress = new SimpleDoubleProperty(0.0);

    /** 侧边栏历史列表刷新信号，翻转此值触发 SideBarController 刷新 */
    public static final BooleanProperty refreshHistory = new SimpleBooleanProperty(false);
}
