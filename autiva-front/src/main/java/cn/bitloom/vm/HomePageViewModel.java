package cn.bitloom.vm;

import cn.bitloom.agentic.event.Event;
import cn.bitloom.agentic.event.EventBus;
import cn.bitloom.agentic.session.Session;
import cn.bitloom.agentic.session.SessionManager;
import cn.bitloom.agentic.session.SessionRespTypeEnum;
import cn.bitloom.agentic.session.SessionTypeEnum;
import cn.bitloom.store.Store;
import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomePageViewModel {

    private Session session;
    private final SessionManager sessionManager;
    private static final String SOURCE = "desktopApp";
    private static final String TARGET = "bitloom";
    @Getter
    private final ObjectProperty<Message> message = new SimpleObjectProperty<>(null);


    @PostConstruct
    public void init() {
        this.session = sessionManager.getOrCreate(SOURCE, SessionTypeEnum.DM, SessionRespTypeEnum.STREAM, TARGET);
        EventBus.outBoxSubscribe()
                .filter(event -> event.getSessionId().equals(this.session.getId()))
                .map(Event::getMessage)
                .subscribe(
                        message -> Platform.runLater(() -> HomePageViewModel.this.message.set(message)),
                        error -> {
                            log.error("Failed to send message", error);
                            Platform.runLater(() -> Store.statusText.set("发送失败: " + error.getMessage()));
                        },
                        () -> {
                            log.info("Message processing completed");
                            Platform.runLater(() -> Store.statusText.set("就绪"));
                        }
                );
    }

    public List<Message> getHistoricalMessages() {
        return this.session.getMessages();
    }

    public void sendMessage(UserMessage message) {
        Platform.runLater(() -> Store.statusText.set("正在处理..."));
        EventBus.inBoxPublish(this.session.getId(), message);
        Platform.runLater(() -> Store.statusText.set("就绪"));
    }

    public void clear() {
        this.message.set(null);
        sessionManager.clearSessionMessages(this.session.getId());
        Platform.runLater(() -> Store.statusText.set("就绪"));
    }
}