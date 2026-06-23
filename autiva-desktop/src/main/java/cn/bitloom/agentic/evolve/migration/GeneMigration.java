package cn.bitloom.agentic.evolve.migration;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import cn.bitloom.agentic.evolve.gene.GeneRuntimeType;
import cn.bitloom.agentic.evolve.gene.GeneStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class GeneMigration {

    private final EvolveConfig config;
    private final GeneStore geneStore;
    private final ObjectMapper mapper;

    public GeneMigration(EvolveConfig config, GeneStore geneStore) {
        this.config = config;
        this.geneStore = geneStore;
        this.mapper = new ObjectMapper();
    }

    public void migrateIfNeeded() {
        Path migrationMarker = config.getGenesDir().resolve(".migrated");
        if (Files.exists(migrationMarker)) {
            log.info("[Evolve] 基因数据已迁移，跳过");
            return;
        }

        if (!Files.exists(config.getGenesFile())) {
            log.info("[Evolve] 无需迁移：genes.json不存在");
            return;
        }

        try {
            String json = Files.readString(config.getGenesFile(), StandardCharsets.UTF_8);
            List<Gene> genes = mapper.readValue(json, new TypeReference<>() {});

            if (genes == null || genes.isEmpty()) {
                log.info("[Evolve] 无需迁移：基因库为空");
                return;
            }

            log.info("[Evolve] 开始迁移 {} 个基因到目录结构", genes.size());

            for (Gene gene : genes) {
                Gene migrated = migrateGene(gene);
                geneStore.upsertGene(migrated);
            }

            Path backupFile = config.getEvolveDir().resolve("genes.json.bak");
            Files.copy(config.getGenesFile(), backupFile);
            Files.writeString(migrationMarker, "migrated at " + System.currentTimeMillis(), StandardCharsets.UTF_8);

            log.info("[Evolve] 基因迁移完成，备份已保存到 genes.json.bak");
        } catch (IOException e) {
            log.error("[Evolve] 基因迁移失败", e);
        }
    }

    private Gene migrateGene(Gene gene) {
        String code = gene.strategy() != null ? String.join("\n", gene.strategy()) : "";

        return new Gene(
                gene.id(),
                gene.category(),
                gene.signalsMatch(),
                gene.preconditions(),
                gene.strategy(),
                gene.constraints(),
                gene.validation(),
                gene.epigeneticBoost(),
                gene.summary(),
                gene.antiPatterns(),
                gene.enabled(),
                GeneRuntimeType.STRATEGY,
                code,
                1,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }
}
