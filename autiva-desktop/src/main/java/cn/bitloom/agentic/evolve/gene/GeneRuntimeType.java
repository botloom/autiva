package cn.bitloom.agentic.evolve.gene;

public enum GeneRuntimeType {

    STRATEGY("strategy"),
    SHELL("shell"),
    JAVA("java"),
    SCRIPT("script");

    private final String code;

    GeneRuntimeType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static GeneRuntimeType fromCode(String code) {
        for (GeneRuntimeType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return STRATEGY;
    }
}
