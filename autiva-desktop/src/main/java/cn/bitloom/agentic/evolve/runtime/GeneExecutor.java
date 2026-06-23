package cn.bitloom.agentic.evolve.runtime;

import cn.bitloom.agentic.evolve.gene.GeneRuntimeType;

public interface GeneExecutor {

    GeneRuntimeType supportedType();

    GeneResult execute(String code, String input);
}
