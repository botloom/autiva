package cn.bitloom.agentic.evolve.execution;

public record ExecutionLog(
        String id,
        long timestamp,
        String taskId,
        String intent,
        String geneId,
        String input,
        String output,
        boolean success,
        String error,
        long durationMs
) {
    public static ExecutionLog success(String id, String taskId, String intent,
                                       String geneId, String input, String output,
                                       long durationMs) {
        return new ExecutionLog(id, System.currentTimeMillis(), taskId, intent,
                geneId, input, output, true, null, durationMs);
    }

    public static ExecutionLog failure(String id, String taskId, String intent,
                                       String geneId, String input, String error,
                                       long durationMs) {
        return new ExecutionLog(id, System.currentTimeMillis(), taskId, intent,
                geneId, input, null, false, error, durationMs);
    }
}
