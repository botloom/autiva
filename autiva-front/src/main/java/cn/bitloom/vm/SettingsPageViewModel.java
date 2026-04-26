package cn.bitloom.vm;

import cn.bitloom.config.ConfigManager;
import cn.bitloom.store.Store;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.BooleanProperty;
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
    private final StringProperty browserPath = new SimpleStringProperty();
    @Getter
    private final StringProperty dingTalkClientId = new SimpleStringProperty();
    @Getter
    private final StringProperty dingTalkClientSecret = new SimpleStringProperty();
    @Getter
    private final BooleanProperty weixinEnabled = new SimpleBooleanProperty();
    @Getter
    private final StringProperty deepseekApiKey = new SimpleStringProperty();
    @Getter
    private final StringProperty zApiKey = new SimpleStringProperty();

    public void loadFromStore() {
        browserPath.set(configManager.getBrowserPath());
        dingTalkClientId.set(configManager.getDingTalkClientId() != null ? configManager.getDingTalkClientId() : "");
        dingTalkClientSecret.set(configManager.getDingTalkClientSecret() != null ? configManager.getDingTalkClientSecret() : "");
        weixinEnabled.set(configManager.isWeixinILinkEnabled());
        deepseekApiKey.set(configManager.getDeepseekApiKey() != null ? configManager.getDeepseekApiKey() : "");
        zApiKey.set(configManager.getZApiKey() != null ? configManager.getZApiKey() : "");
    }

    public void save() {
        configManager.setBrowserPath(browserPath.get());
        configManager.setDingTalkClientId(dingTalkClientId.get());
        configManager.setDingTalkClientSecret(dingTalkClientSecret.get());
        configManager.setWeixinILinkEnabled(weixinEnabled.get());
        configManager.setDeepseekApiKey(deepseekApiKey.get());
        configManager.setZApiKey(zApiKey.get());
        configManager.save();
        Store.statusText.set("配置已保存");
    }

    public void reset() {
        configManager.setBrowserPath("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        configManager.setDingTalkClientId("");
        configManager.setDingTalkClientSecret("");
        configManager.setWeixinILinkEnabled(false);
        configManager.setDeepseekApiKey("");
        configManager.setZApiKey("");
        configManager.save();
        loadFromStore();
        Store.statusText.set("重置成功");
    }
}
