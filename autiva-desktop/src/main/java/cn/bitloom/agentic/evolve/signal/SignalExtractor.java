package cn.bitloom.agentic.evolve.signal;

import cn.bitloom.exception.AutivaException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SignalExtractor {

    private static final Map<Pattern, SignalType> ERROR_PATTERNS = Map.of(
            Pattern.compile("(?i)(exception|error|failed|failure|崩溃|异常|失败)"), SignalType.LOG_ERROR,
            Pattern.compile("(?i)(403|401|forbidden|unauthorized|权限|认证)"), SignalType.LOG_ERROR,
            Pattern.compile("(?i)(timeout|超时|timed out)"), SignalType.PERF_BOTTLENECK,
            Pattern.compile("(?i)(not found|不存在|404)"), SignalType.CAPABILITY_GAP
    );

    private static final Map<String, SignalType> KEYWORD_MAP = Map.ofEntries(
            Map.entry("慢", SignalType.PERF_BOTTLENECK),
            Map.entry("slow", SignalType.PERF_BOTTLENECK),
            Map.entry("性能", SignalType.PERF_BOTTLENECK),
            Map.entry("performance", SignalType.PERF_BOTTLENECK),
            Map.entry("瓶颈", SignalType.PERF_BOTTLENECK),
            Map.entry("bottleneck", SignalType.PERF_BOTTLENECK),
            Map.entry("希望", SignalType.USER_FEATURE_REQUEST),
            Map.entry("能不能", SignalType.USER_FEATURE_REQUEST),
            Map.entry("可以加", SignalType.USER_FEATURE_REQUEST),
            Map.entry("wish", SignalType.USER_FEATURE_REQUEST),
            Map.entry("could you", SignalType.USER_FEATURE_REQUEST),
            Map.entry("feature", SignalType.USER_FEATURE_REQUEST),
            Map.entry("改进", SignalType.USER_IMPROVEMENT_SUGGESTION),
            Map.entry("优化", SignalType.USER_IMPROVEMENT_SUGGESTION),
            Map.entry("improve", SignalType.USER_IMPROVEMENT_SUGGESTION),
            Map.entry("suggest", SignalType.USER_IMPROVEMENT_SUGGESTION),
            Map.entry("bypass", SignalType.TOOL_BYPASS),
            Map.entry("绕过", SignalType.TOOL_BYPASS),
            Map.entry("跳过", SignalType.TOOL_BYPASS)
    );

    public List<Signal> extract(List<String> messages) {
        List<Signal> signals = new ArrayList<>();
        for (String message : messages) {
            extractFromText(message, "conversation", signals);
        }
        return deduplicate(signals);
    }

    public List<Signal> extractFromText(String text, String source, List<Signal> accumulator) {
        for (Map.Entry<Pattern, SignalType> entry : ERROR_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(text).find()) {
                accumulator.add(Signal.of(entry.getValue(), truncate(text, 200), source));
            }
        }

        for (Map.Entry<String, SignalType> entry : KEYWORD_MAP.entrySet()) {
            if (text.toLowerCase().contains(entry.getKey().toLowerCase())) {
                accumulator.add(Signal.of(entry.getValue(), truncate(text, 200), source));
            }
        }

        return accumulator;
    }

    public Signal extractFromException(AutivaException ex) {
        SignalType signalType = ex.toSignalType();
        String content = ex.getErrorCode() + ": " + ex.getMessage();
        return Signal.of(signalType, truncate(content, 200), "exception");
    }

    public List<Signal> extractFromExceptions(List<? extends AutivaException> exceptions) {
        List<Signal> signals = new ArrayList<>();
        for (AutivaException ex : exceptions) {
            signals.add(extractFromException(ex));
        }
        return deduplicate(signals);
    }

    private List<Signal> deduplicate(List<Signal> signals) {
        Map<String, Signal> unique = new java.util.LinkedHashMap<>();
        for (Signal s : signals) {
            String key = s.type().code();
            if (!unique.containsKey(key) || s.timestamp() > unique.get(key).timestamp()) {
                unique.put(key, s);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
