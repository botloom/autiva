package cn.bitloom.controller;

import cn.bitloom.sandbox.RouteTarget;
import cn.bitloom.sandbox.SubdomainRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
public class GatewayController {

    private final SubdomainRouter subdomainRouter;

    @GetMapping("/resolve")
    public Mono<RouteTarget> resolve(String host) {
        return subdomainRouter.resolve(host);
    }
}
