package cn.bitloom.baas;

import cn.bitloom.config.BaasProperties;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
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
        String keyPrefix = sanitizeName(serviceId) + ":";
        BaasProperties.Redis redis = baasProperties.getRedis();

        String url;
        if (redis.getPassword() != null && !redis.getPassword().isEmpty()) {
            url = String.format("redis://:%s@%s:%d", redis.getPassword(), redis.getHost(), redis.getPort());
        } else {
            url = String.format("redis://%s:%d", redis.getHost(), redis.getPort());
        }

        return Mono.just(new BaasResource(
                "redis",
                keyPrefix,
                new JSONObject(Map.of(
                        "host", redis.getHost(),
                        "port", redis.getPort(),
                        "password", redis.getPassword(),
                        "keyPrefix", keyPrefix,
                        "url", url
                ))
        ));
    }

    public Mono<BaasResource> createMongodbDatabase(String serviceId) {
        String dbName = sanitizeName(serviceId);
        String username = "user_" + sanitizeName(serviceId);
        String password = generatePassword();

        BaasProperties.Mongodb mongodb = baasProperties.getMongodb();

        return Mono.fromCallable(() -> {
                    try {
                        String adminUri;
                        if (mongodb.getUsername() != null && !mongodb.getUsername().isEmpty()) {
                            adminUri = String.format("mongodb://%s:%s@%s:%d/admin",
                                    mongodb.getUsername(), mongodb.getPassword(),
                                    mongodb.getHost(), mongodb.getPort());
                        } else {
                            adminUri = String.format("mongodb://%s:%d/admin",
                                    mongodb.getHost(), mongodb.getPort());
                        }

                        try (MongoClient mongoClient = MongoClients.create(adminUri)) {
                            MongoDatabase adminDb = mongoClient.getDatabase("admin");
                            adminDb.runCommand(new Document("createUser", username)
                                    .append("pwd", password)
                                    .append("roles", Arrays.asList(
                                            new Document("role", "readWrite").append("db", dbName)
                                    )));
                            log.info("MongoDB database and user created: {}", dbName);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to create MongoDB database, generating config only: {}", e.getMessage());
                    }
                    return true;
                })
                .thenReturn(new BaasResource(
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
        String bucketName = sanitizeName(serviceId).replace("_", "-");

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
                                "accessKey", minio.getAccessKey(),
                                "secretKey", minio.getSecretKey(),
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
                createMysqlDatabase(serviceId)
                        .doOnNext(r -> resources.put("mysql", r))
                        .onErrorResume(e -> {
                            log.warn("Failed to create MySQL resource, skipping: {}", e.getMessage());
                            return Mono.empty();
                        }),
                createRedisNamespace(serviceId)
                        .doOnNext(r -> resources.put("redis", r))
                        .onErrorResume(e -> {
                            log.warn("Failed to create Redis resource, skipping: {}", e.getMessage());
                            return Mono.empty();
                        }),
                createMongodbDatabase(serviceId)
                        .doOnNext(r -> resources.put("mongodb", r))
                        .onErrorResume(e -> {
                            log.warn("Failed to create MongoDB resource, skipping: {}", e.getMessage());
                            return Mono.empty();
                        }),
                createMinioBucket(serviceId)
                        .doOnNext(r -> resources.put("minio", r))
                        .onErrorResume(e -> {
                            log.warn("Failed to create MinIO resource, skipping: {}", e.getMessage());
                            return Mono.empty();
                        })
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
            envVars.put("REDIS_KEY_PREFIX", info.getString("keyPrefix"));
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
