package cn.bitloom.baas;

import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaasManager {

    private static final String MYSQL_HOST = "autiva-mysql";
    private static final int MYSQL_PORT = 3306;
    private static final String REDIS_HOST = "autiva-redis";
    private static final int REDIS_PORT = 6379;
    private static final String MONGODB_HOST = "autiva-mongodb";
    private static final int MONGODB_PORT = 27017;
    private static final String MINIO_HOST = "autiva-minio";
    private static final int MINIO_PORT = 9000;
    private static final String RABBITMQ_HOST = "autiva-rabbitmq";
    private static final int RABBITMQ_PORT = 5672;

    public Mono<BaasResource> createMysqlDatabase(String serviceId) {
        String dbName = "db_" + sanitizeName(serviceId);
        String username = "user_" + sanitizeName(serviceId);
        String password = generatePassword();

        return Mono.just(new BaasResource(
                "mysql",
                dbName,
                new JSONObject(Map.of(
                        "host", MYSQL_HOST,
                        "port", MYSQL_PORT,
                        "database", dbName,
                        "username", username,
                        "password", password,
                        "jdbcUrl", String.format("jdbc:mysql://%s:%d/%s", MYSQL_HOST, MYSQL_PORT, dbName)
                ))
        ));
    }

    public Mono<BaasResource> createRedisNamespace(String serviceId) {
        String namespace = "ns_" + sanitizeName(serviceId);
        String password = generatePassword();

        return Mono.just(new BaasResource(
                "redis",
                namespace,
                new JSONObject(Map.of(
                        "host", REDIS_HOST,
                        "port", REDIS_PORT,
                        "password", password,
                        "namespace", namespace,
                        "url", String.format("redis://:%s@%s:%d/%s", password, REDIS_HOST, REDIS_PORT, namespace)
                ))
        ));
    }

    public Mono<BaasResource> createMongodbDatabase(String serviceId) {
        String dbName = "db_" + sanitizeName(serviceId);
        String username = "user_" + sanitizeName(serviceId);
        String password = generatePassword();

        return Mono.just(new BaasResource(
                "mongodb",
                dbName,
                new JSONObject(Map.of(
                        "host", MONGODB_HOST,
                        "port", MONGODB_PORT,
                        "database", dbName,
                        "username", username,
                        "password", password,
                        "uri", String.format("mongodb://%s:%s@%s:%d/%s", username, password, MONGODB_HOST, MONGODB_PORT, dbName)
                ))
        ));
    }

    public Mono<BaasResource> createMinioBucket(String serviceId) {
        String bucketName = "bucket-" + sanitizeName(serviceId);
        String accessKey = "ak_" + generatePassword().substring(0, 20);
        String secretKey = "sk_" + generatePassword();

        return Mono.just(new BaasResource(
                "minio",
                bucketName,
                new JSONObject(Map.of(
                        "endpoint", String.format("http://%s:%d", MINIO_HOST, MINIO_PORT),
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

        return Mono.just(new BaasResource(
                "rabbitmq",
                vhost,
                new JSONObject(Map.of(
                        "host", RABBITMQ_HOST,
                        "port", RABBITMQ_PORT,
                        "vhost", vhost,
                        "username", username,
                        "password", password,
                        "url", String.format("amqp://%s:%s@%s:%d/%s", username, password, RABBITMQ_HOST, RABBITMQ_PORT, vhost)
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

    private String sanitizeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "_").substring(0, Math.min(name.length(), 32));
    }

    private String generatePassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
