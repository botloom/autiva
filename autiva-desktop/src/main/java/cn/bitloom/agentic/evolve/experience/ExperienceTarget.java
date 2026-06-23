package cn.bitloom.agentic.evolve.experience;

public enum ExperienceTarget {

    GENE("gene"),
    ROUTING("routing"),
    MEMORY("memory");

    private final String code;

    ExperienceTarget(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ExperienceTarget fromCode(String code) {
        for (ExperienceTarget t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return GENE;
    }
}
