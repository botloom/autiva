package cn.bitloom.agentic.evolve.signal;

public enum SignalType {

    LOG_ERROR("log_error"),
    ERRSIG("errsig"),
    RECURRING_ERROR("recurring_error"),

    USER_FEATURE_REQUEST("user_feature_request"),
    USER_IMPROVEMENT_SUGGESTION("user_improvement_suggestion"),

    PERF_BOTTLENECK("perf_bottleneck"),
    CAPABILITY_GAP("capability_gap"),

    STABLE_SUCCESS_PLATEAU("stable_success_plateau"),
    EVOLUTION_STAGNATION("evolution_stagnation"),

    REPAIR_LOOP_DETECTED("repair_loop_detected"),
    FORCE_INNOVATION_AFTER_REPAIR_LOOP("force_innovation_after_repair_loop"),

    TOOL_BYPASS("tool_bypass"),
    HIGH_TOOL_USAGE("high_tool_usage"),

    MEMORY_MISSING("memory_missing"),
    SESSION_LOGS_MISSING("session_logs_missing"),

    EXPLORE_OPPORTUNITY("explore_opportunity"),
    EVOLUTION_SATURATION("evolution_saturation");

    private final String code;

    SignalType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SignalType fromCode(String code) {
        for (SignalType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return LOG_ERROR;
    }
}
