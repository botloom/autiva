package cn.bitloom.agentic.evolve.signal;

public record Signal(
        SignalType type,
        String content,
        long timestamp,
        String source
) {
    public Signal {
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
    }

    public static Signal of(SignalType type, String content) {
        return new Signal(type, content, System.currentTimeMillis(), "auto");
    }

    public static Signal of(SignalType type, String content, String source) {
        return new Signal(type, content, System.currentTimeMillis(), source);
    }
}
