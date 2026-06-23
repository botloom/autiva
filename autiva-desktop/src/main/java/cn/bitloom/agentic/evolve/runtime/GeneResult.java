package cn.bitloom.agentic.evolve.runtime;

public record GeneResult(
        boolean success,
        String output,
        String error,
        long durationMs
) {
    public static GeneResult ok(String output, long durationMs) {
        return new GeneResult(true, output, null, durationMs);
    }

    public static GeneResult fail(String error, long durationMs) {
        return new GeneResult(false, null, error, durationMs);
    }
}
