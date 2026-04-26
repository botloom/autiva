package cn.bitloom.baas;

import cn.bitloom.config.BaasProperties;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasManager {

    private final BaasProperties baasProperties;

    public Mono<BaasResource> createMysqlDatabase(String serviceId) {
        String dbName = "db_" + sanitizeName(serviceId);
        String username = "user_" + sanitizeName(serviceId);
        String password = generatePassword();

        BaasProperties.Mysql mysql = baasProperties.getMysql();

        return Mono.fromCallable(() -> {
                    try (Connection conn = DriverManager.getConnection(
                            String.format("jdbc:mysql://%s:%d/?useSSL=false&allowPublicKeyRetrieval=true",
                                    mysql.getHost(), mysql.getPort()),
                            mysql.getUsername(), mysql.getPassword())) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + dbName +
                                    "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                            stmt.execute("CREATE USER IF NOT EXISTS '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
                            stmt.execute("GRANT ALL PRIVILEGES ON `" + dbName + "`.* TO '" + username + "'@'%'");
                            stmt.execute("FLUSH PRIVILEGES");
                        }
                        log.info("MySQL database created: {}", dbName);
                    } catch (Exception e) {
                        log.warn("Failed to create MySQL database, generating config only: {}", e.getMessage());
                    }
                    return true;
                })
                .thenReturn(new BaasResource(
                        "mysql",
                        dbName,
                        new JSONObject(Map.of(
                                "host", mysql.getHost(),
                                "port", mysql.getPort(),
                                "database", dbName,
                                "username", username,
                                "password", password,
                                "jdbcUrl", String.format("jdbc:mysql://%s:%d/%s", mysql.getHost(), mysql.getPort(), dbName)
                        ))
                ));
    }

    public Mono<BaasResource> createRedisNamespace(String serviceId) {
        String namespace = "ns_" + sanitizeName(serviceId);
        String password = generatePassword();

        BaasProperties.Redis redis = baasProperties.getRedis();

        return Mono.just(new BaasResource(
                "redis",
                namespace,
                new JSONObject(Map.of(
                        "host", redis.getHost(),
                        "port", redis.getPort(),
                        "password", password,
                        "namespace", namespace,
                        "url", String.format("redis://:%s@%s:%d", password, redis.getHost(), redis.getPort())
                ))
        ));
    }

    public Mono<BaasResource> createMongodbDatabase(String serviceId) {
        String dbName = "db_" + sanitizeName(serviceId);
        String username = "user_" + sanitizeName(serviceId);
        String password = generatePassword();

        BaasProperties.Mongodb mongodb = baasProperties.getMongodb();

        return Mono.just(new BaasResource(
                "mongodb",
                dbName,
                new JSONObject(Map.of(
                        "host", mongodb.getHost(),
                        "port", mongodb.getPort(),
                        "database", dbName,
                        "username", username,
                        "password", password,
                        "uri", String.format("mongodb://%s:%s@%s:%d/%s",
                                username, password, mongodb.getHost(), mongodb.getPort(), dbName)
                ))
        ));
    }

    public Mono<BaasResource> createMinioBucket(String serviceId) {
        String bucketName = "bucket-" + sanitizeName(serviceId);
        String accessKey = "ak_" + generatePassword().substring(0, 20);
        String secretKey = "sk_" + generatePassword();

        BaasProperties.Minio minio = baasProperties.getMinio();

        return Mono.fromCallable(() -> {
                    try {
                        io.minio.MinioClient minioClient = io.minio.MinioClient.builder()
                                .endpoint(minio.getEndpoint())
                                .credentials(minio.getAccessKey(), minio.getSecretKey())
                                .build();
                        boolean exists = minioClient.bucketExists(
                                io.minio.BucketExistsArgs.builder()
                                        .bucket(bucketName).build());
                        if (!exists) {
                            minioClient.makeBucket(
                                    io.minio.MakeBucketArgs.builder()
                                            .bucket(bucketName).build());
                            log.info("MinIO bucket created: {}", bucketName);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to create MinIO bucket, generating config only: {}", e.getMessage());
                    }
                    return true;
                })
                .thenReturn(new BaasResource(
                        "minio",
                        bucketName,
                        new JSONObject(Map.of(
                                "endpoint", minio.getEndpoint(),
                                "bucket", bucketName,
                                "accessKey", accessKey,
                                "secretKey", secretKey,
                                "region", "us-east-1"
                        ))
                ));
    }

    public Mono<BaasResource> createRabbitmqQueue(String serviceId) {
        String vhost = "vhost_" + sanitizeName(serviceId);
        String username = "user_" + sanitizeName(serviceId);
        String password = generatePassword();

        BaasProperties.Rabbitmq rabbitmq = baasProperties.getRabbitmq();

        return Mono.just(new BaasResource(
                "rabbitmq",
                vhost,
                new JSONObject(Map.of(
                        "host", rabbitmq.getHost(),
                        "port", rabbitmq.getPort(),
                        "vhost", vhost,
                        "username", username,
                        "password", password,
                        "url", String.format("amqp://%s:%s@%s:%d/%s",
                                username, password, rabbitmq.getHost(), rabbitmq.getPort(), vhost)
                ))
        ));
    }

    public Mono<Map<String, BaasResource>> createAllResources(String serviceId) {
        Map<String, BaasResource> resources = new HashMap<>();

        return Mono.when(
                createMysqlDatabase(serviceId).map(r -> resources.put("mysql", r)),
                createRedisNamespace(serviceId).map(r -> resources.put("redis", r)),
                createMongodbDatabase(serviceId).map(r -> resources.put("mongodb", r)),
                createMinioBucket(serviceId).map(r -> resources.put("minio", r))
        ).then(Mono.just(resources));
    }

    public Map<String, String> resourcesToEnvVars(Map<String, BaasResource> resources) {
        Map<String, String> envVars = new HashMap<>();

        BaasResource mysql = resources.get("mysql");
        if (mysql != null) {
            JSONObject info = mysql.connectionInfo();
            envVars.put("DATABASE_HOST", info.getString("host"));
            envVars.put("DATABASE_PORT", String.valueOf(info.getIntValue("port")));
            envVars.put("DATABASE_NAME", info.getString("database"));
            envVars.put("DATABASE_USER", info.getString("username"));
            envVars.put("DATABASE_PASSWORD", info.getString("password"));
            envVars.put("DATABASE_URL", info.getString("jdbcUrl"));
            envVars.put("MYSQL_HOST", info.getString("host"));
            envVars.put("MYSQL_PORT", String.valueOf(info.getIntValue("port")));
            envVars.put("MYSQL_DATABASE", info.getString("database"));
            envVars.put("MYSQL_USER", info.getString("username"));
            envVars.put("MYSQL_PASSWORD", info.getString("password"));
        }

        BaasResource redis = resources.get("redis");
        if (redis != null) {
            JSONObject info = redis.connectionInfo();
            envVars.put("REDIS_HOST", info.getString("host"));
            envVars.put("REDIS_PORT", String.valueOf(info.getIntValue("port")));
            envVars.put("REDIS_PASSWORD", info.getString("password"));
            envVars.put("REDIS_URL", info.getString("url"));
        }

        BaasResource mongodb = resources.get("mongodb");
        if (mongodb != null) {
            JSONObject info = mongodb.connectionInfo();
            envVars.put("MONGODB_HOST", info.getString("host"));
            envVars.put("MONGODB_PORT", String.valueOf(info.getIntValue("port")));
            envVars.put("MONGODB_DATABASE", info.getString("database"));
            envVars.put("MONGODB_USER", info.getString("username"));
            envVars.put("MONGODB_PASSWORD", info.getString("password"));
            envVars.put("MONGODB_URI", info.getString("uri"));
        }

        BaasResource minio = resources.get("minio");
        if (minio != null) {
            JSONObject info = minio.connectionInfo();
            envVars.put("MINIO_ENDPOINT", info.getString("endpoint"));
            envVars.put("MINIO_BUCKET", info.getString("bucket"));
            envVars.put("MINIO_ACCESS_KEY", info.getString("accessKey"));
            envVars.put("MINIO_SECRET_KEY", info.getString("secretKey"));
            envVars.put("MINIO_REGION", info.getString("region"));
        }

        return envVars;
    }

    private String sanitizeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "_").substring(0, Math.min(name.length(), 32));
    }

    private String generatePassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
