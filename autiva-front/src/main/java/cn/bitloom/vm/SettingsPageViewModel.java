package cn.bitloom.vm;

import cn.bitloom.config.ConfigManager;
import cn.bitloom.store.Store;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsPageViewModel {

    private final ConfigManager configManager;

    @Getter
    private final StringProperty dingTalkClientId = new SimpleStringProperty();
    @Getter
    private final StringProperty dingTalkClientSecret = new SimpleStringProperty();
    @Getter
    private final StringProperty bochaApiKey = new SimpleStringProperty();
    @Getter
    private final StringProperty deepseekBaseUrl = new SimpleStringProperty();
    @Getter
    private final StringProperty deepseekCompletionsPath = new SimpleStringProperty();
    @Getter
    private final StringProperty deepseekApiKey = new SimpleStringProperty();
    @Getter
    private final StringProperty deepseekChatModel = new SimpleStringProperty();


    public void loadFromStore() {
        dingTalkClientId.set(configManager.getDingTalkClientId());
        dingTalkClientSecret.set(configManager.getDingTalkClientSecret());
        bochaApiKey.set(configManager.getBochaApiKey());
        deepseekBaseUrl.set(configManager.getDeepseekBaseUrl());
        deepseekCompletionsPath.set(configManager.getDeepseekCompletionsPath());
        deepseekApiKey.set(configManager.getDeepseekApiKey());
        deepseekChatModel.set(configManager.getDeepseekChatModel());
    }

    public void save() {
        configManager.setDingTalkClientId(dingTalkClientId.get());
        configManager.setDingTalkClientSecret(dingTalkClientSecret.get());
        configManager.setBochaApiKey(bochaApiKey.get());
        configManager.setDeepseekBaseUrl(deepseekBaseUrl.get());
        configManager.setDeepseekCompletionsPath(deepseekCompletionsPath.get());
        configManager.setDeepseekApiKey(deepseekApiKey.get());
        configManager.setDeepseekChatModel(deepseekChatModel.get());
        configManager.save();
        Store.statusText.set("配置已保存");
    }

    public void reset() {
        configManager.setDingTalkClientId(null);
        configManager.setDingTalkClientSecret(null);
        configManager.setBochaApiKey(null);
        configManager.setDeepseekBaseUrl(null);
        configManager.setDeepseekCompletionsPath(null);
        configManager.setDeepseekApiKey(null);
        configManager.setDeepseekChatModel(null);
        configManager.save();
        loadFromStore();
        Store.statusText.set("重置成功");
    }
}
