package cn.bitloom.controller;

import cn.bitloom.holder.PageHolder;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@Component
public class AgentPageController implements Initializable, PageHolder {

    @FXML
    private VBox agentPage;

    @Getter
    @Setter
    private IndexController indexController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    @Override
    public void show() {
        this.agentPage.setVisible(true);
        this.agentPage.setManaged(true);
    }

    @Override
    public void hide() {
        this.agentPage.setVisible(false);
        this.agentPage.setManaged(false);
    }
}
