package cn.bitloom.controller;

import cn.bitloom.sandbox.RouteTarget;
import cn.bitloom.sandbox.SubdomainRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
public class GatewayController {

    private final SubdomainRouter subdomainRouter;

    @GetMapping("/resolve")
    public Mono<RouteTarget> resolve(@RequestParam String host) {
        return subdomainRouter.resolve(host);
    }
}
