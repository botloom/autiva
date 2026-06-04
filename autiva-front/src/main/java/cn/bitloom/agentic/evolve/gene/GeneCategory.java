package cn.bitloom.agentic.evolve.gene;

public enum GeneCategory {
    REPAIR("repair"),
    OPTIMIZE("optimize"),
    INNOVATE("innovate");

    private final String code;

    GeneCategory(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static GeneCategory fromCode(String code) {
        for (GeneCategory c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        return REPAIR;
    }
}
