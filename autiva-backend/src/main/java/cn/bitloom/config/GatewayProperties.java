package cn.bitloom.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private String baseDomain;
    private String defaultTarget;
}
