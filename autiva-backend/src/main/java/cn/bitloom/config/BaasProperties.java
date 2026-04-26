package cn.bitloom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "baas")
public class BaasProperties {

    private Mysql mysql = new Mysql();
    private Redis redis = new Redis();
    private Mongodb mongodb = new Mongodb();
    private Minio minio = new Minio();
    private Rabbitmq rabbitmq = new Rabbitmq();

    @Data
    public static class Mysql {
        private String host = "localhost";
        private int port = 3306;
        private String username = "root";
        private String password = "";
    }

    @Data
    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
    }

    @Data
    public static class Mongodb {
        private String host = "localhost";
        private int port = 27017;
        private String username = "";
        private String password = "";
    }

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "";
        private String secretKey = "";
    }

    @Data
    public static class Rabbitmq {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
    }
}
