package cn.bitloom;

import cn.bitloom.bootstrap.AppBootstrap;
import cn.bitloom.bootstrap.SplashScreen;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.ExecutorManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration;
import org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.sendgrid.SendGridAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.*;
import org.springframework.boot.autoconfigure.web.reactive.error.ErrorWebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.*;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.reactive.WebSocketReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

@Slf4j
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        JdbcClientAutoConfiguration.class,
        XADataSourceAutoConfiguration.class,
        JmxAutoConfiguration.class,
        ReactiveWebServerFactoryAutoConfiguration.class,
        HttpHandlerAutoConfiguration.class,
        WebFluxAutoConfiguration.class,
        ErrorWebFluxAutoConfiguration.class,
        ReactiveMultipartAutoConfiguration.class,
        WebSessionIdResolverAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        ServletWebServerFactoryAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpEncodingAutoConfiguration.class,
        MultipartAutoConfiguration.class,
        ErrorMvcAutoConfiguration.class,
        WebSocketReactiveAutoConfiguration.class,
        WebSocketServletAutoConfiguration.class,
        WebSocketMessagingAutoConfiguration.class,
        ReactiveSecurityAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        SessionAutoConfiguration.class,
        MailSenderAutoConfiguration.class,
        MailSenderValidatorAutoConfiguration.class,
        FreeMarkerAutoConfiguration.class,
        ThymeleafAutoConfiguration.class,
        MustacheAutoConfiguration.class,
        GroovyTemplateAutoConfiguration.class,
        CacheAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        JtaAutoConfiguration.class,
        PersistenceExceptionTranslationAutoConfiguration.class,
        RSocketServerAutoConfiguration.class,
        RSocketMessagingAutoConfiguration.class,
        RSocketRequesterAutoConfiguration.class,
        RSocketStrategiesAutoConfiguration.class,
        SslAutoConfiguration.class,
        SendGridAutoConfiguration.class,
})
public class AutivaApplication extends Application {

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void init() {
    }

    @Override
    public void start(Stage stage) {
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(AppConstants.Stage.ICON)));
        SplashScreen splash = new SplashScreen(icon);
        Stage splashStage = splash.show();

        Thread loadingThread = new Thread(() -> {
            try {
                AppBootstrap.initialize();
                springContext = SpringApplication.run(AutivaApplication.class);
            } catch (Exception e) {
                log.error("启动失败", e);
                Platform.exit();
                return;
            }

            // Spring 就绪后，在 FX 线程加载 FXML 并切换窗口
            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConstants.Stage.FXML));
                    loader.setControllerFactory(springContext::getBean);
                    Scene mainScene = new Scene(loader.load(), AppConstants.Stage.WIDTH, AppConstants.Stage.HEIGHT);

                    splash.close();
                    splashStage.close();

                    stage.initStyle(StageStyle.UNIFIED);
                    stage.setScene(mainScene);
                    stage.getIcons().add(icon);
                    stage.setMinWidth(600);
                    stage.setMinHeight(400);
                    stage.show();
                } catch (Exception e) {
                    log.error("主界面加载失败", e);
                }
            });
        }, "app-bootstrap");
        loadingThread.setDaemon(true);
        loadingThread.start();
    }

    @Override
    public void stop() {
        ExecutorManager.close();
        springContext.close();
        // 钉钉 Stream SDK 的 NetworkSharedResources 创建了静态 NioEventLoopGroup（非守护线程），
        // SDK 的 stop() 不会关闭它，导致 JVM 无法正常退出
        System.exit(0);
    }
}
