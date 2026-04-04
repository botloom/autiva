package cn.bitloom.controller;

import cn.bitloom.sandbox.SandboxInfo;
import cn.bitloom.sandbox.SandboxService;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;

    @GetMapping("/{subdomain}")
    public Mono<SandboxInfo> getSandboxBySubdomain(@PathVariable String subdomain) {
        return sandboxService.getSandboxBySubdomain(subdomain);
    }

    @GetMapping("/{subdomain}/details")
    public Mono<JSONObject> getServiceWithResources(@PathVariable String subdomain) {
        return sandboxService.getServiceWithResources(subdomain);
    }
}
