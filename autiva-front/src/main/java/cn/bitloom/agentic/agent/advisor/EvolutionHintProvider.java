package cn.bitloom.agentic.agent.advisor;

import cn.bitloom.agentic.evolve.signal.Signal;
import cn.bitloom.agentic.evolve.signal.SignalExtractor;
import cn.bitloom.agentic.evolve.signal.SignalType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EvolutionHintProvider {

    private final SignalExtractor signalExtractor;

    private final ConcurrentHashMap<SignalType, Instant> lastHintTime = new ConcurrentHashMap<>();

    private static final Duration THROTTLE_INTERVAL = Duration.ofMinutes(30);

    public EvolutionHintProvider() {
        this.signalExtractor = new SignalExtractor();
    }

    public String getHint(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        List<Signal> signals = signalExtractor.extract(List.of(userMessage));
        if (signals.isEmpty()) {
            return null;
        }

        Instant now = Instant.now();
        List<Signal> activeSignals = signals.stream()
                .filter(s -> {
                    Instant last = lastHintTime.get(s.type());
                    return last == null || last.plus(THROTTLE_INTERVAL).isBefore(now);
                })
                .toList();

        if (activeSignals.isEmpty()) {
            return null;
        }

        activeSignals.forEach(s -> lastHintTime.put(s.type(), now));

        String signalNames = activeSignals.stream()
                .map(s -> s.type().code())
                .collect(Collectors.joining(", "));

        log.debug("检测到进化信号: {}", signalNames);

        return """
                <system-reminder>
                检测到以下进化信号：%s
                建议使用 evolve_query 工具查询相关进化建议，或使用 evolve_apply 工具应用策略指导。
                </system-reminder>
                """.formatted(signalNames);
    }
}
