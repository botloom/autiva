package cn.bitloom.sandbox;

import cn.bitloom.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubdomainRouter {

    private final GatewayProperties properties;
    private final WebClient webClient = WebClient.create();

    public Mono<RouteTarget> resolve(String host) {
        if (!host.endsWith("." + properties.getBaseDomain())) {
            return Mono.just(new RouteTarget(properties.getDefaultTarget(), false));
        }

        String subdomain = host.replace("." + properties.getBaseDomain(), "");
        log.debug("Resolving subdomain: {}", subdomain);

        return fetchSandboxInfo(subdomain)
                .map(info -> new RouteTarget(buildTargetUrl(info), true))
                .defaultIfEmpty(new RouteTarget(properties.getDefaultTarget(), false));
    }

    private Mono<SandboxInfo> fetchSandboxInfo(String subdomain) {
        return webClient.get()
                .uri("/api/sandbox/" + subdomain)
                .retrieve()
                .bodyToMono(SandboxInfo.class)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch sandbox info for {}: {}", subdomain, e.getMessage());
                    return Mono.empty();
                });
    }

    private String buildTargetUrl(SandboxInfo info) {
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
