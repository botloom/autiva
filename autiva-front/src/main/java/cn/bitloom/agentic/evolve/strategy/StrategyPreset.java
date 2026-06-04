package cn.bitloom.agentic.evolve.strategy;

public enum StrategyPreset {

    BALANCED(0.50, 0.30, 0.20),
    INNOVATE(0.80, 0.15, 0.05),
    HARDEN(0.20, 0.40, 0.40),
    REPAIR_ONLY(0.00, 0.20, 0.80),
    EARLY_STABILIZE(0.30, 0.35, 0.35),
    STEADY_STATE(0.40, 0.35, 0.25),
    AUTO(0, 0, 0);

    private final double innovateWeight;
    private final double optimizeWeight;
    private final double repairWeight;

    StrategyPreset(double innovateWeight, double optimizeWeight, double repairWeight) {
        this.innovateWeight = innovateWeight;
        this.optimizeWeight = optimizeWeight;
        this.repairWeight = repairWeight;
    }

    public double innovateWeight() {
        return innovateWeight;
    }

    public double optimizeWeight() {
        return optimizeWeight;
    }

    public double repairWeight() {
        return repairWeight;
    }

    public double weightFor(String category) {
        return switch (category.toLowerCase()) {
            case "innovate" -> innovateWeight;
            case "optimize" -> optimizeWeight;
            case "repair" -> repairWeight;
            default -> 0;
        };
    }
}
