package cn.bitloom.sandbox;

import cn.bitloom.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubdomainRouter {

    private final GatewayProperties properties;
    private final SandboxService sandboxService;
    private final SandboxManager sandboxManager;

    public Mono<RouteTarget> resolve(String host) {
        if (!host.endsWith("." + properties.getBaseDomain())) {
            return Mono.just(new RouteTarget(properties.getDefaultTarget(), false));
        }

        String subdomain = host.replace("." + properties.getBaseDomain(), "");
        log.debug("Resolving subdomain: {}", subdomain);

        return sandboxService.getServiceBySubdomain(subdomain)
                .map(info -> new RouteTarget(buildTargetUrl(info), true))
                .defaultIfEmpty(new RouteTarget(properties.getDefaultTarget(), false));
    }

    private String buildTargetUrl(SandboxInfo info) {
        String endpoint = sandboxManager.getEndpoint(info.containerId(), info.runtime());
        if (endpoint != null) {
            return endpoint;
        }
        // fallback: 使用默认端口
        return "http://localhost:" + getPortForRuntime(info.runtime());
    }

    private int getPortForRuntime(String runtime) {
        return switch (runtime) {
            case "node" -> 3000;
            case "python" -> 8000;
            case "java" -> 8080;
            default -> 3000;
        };
    }
}
