package cn.bitloom.agentic.evolve.routing;

import cn.bitloom.agentic.evolve.gene.GeneRepository;
import cn.bitloom.constant.AppConstants;
import cn.bitloom.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 路由引擎 — 根据当前信号决定激活哪些基因。
 * <p>
 * 路由表持久化在 {@link AppConstants.Evolve#ROUTING_FILE}，以 JSON 数组形式存储。
 * 路由时过滤 enabled 条目并按 priority 降序排序，返回激活顺序。
 */
@Slf4j
public class RoutingEngine {

    private final GeneRepository geneRepository;

    public RoutingEngine(GeneRepository geneRepository) {
        this.geneRepository = geneRepository;
    }

    /**
     * 根据当前信号路由激活的基因条目。
     * <p>
     * 读取 routing.json，过滤 enabled=true 的条目，按 priority 降序排序后返回。
     * 文件不存在时返回空列表。
     *
     * @param taskType      当前任务类型（保留用于未来基于 condition 的匹配）
     * @param toolsUsed     当前使用的工具列表（保留用于未来匹配）
     * @param errorPatterns 当前错误模式列表（保留用于未来匹配）
     * @return 按 priority 降序排列的激活路由条目
     */
    public List<RoutingEntry> route(String taskType, List<String> toolsUsed, List<String> errorPatterns) {
        log.debug("[RoutingEngine] 路由请求: taskType={}, toolsUsed={}, errorPatterns={}",
                taskType, toolsUsed, errorPatterns);
        List<RoutingEntry> all = loadAllEntries();
        return all.stream()
                .filter(RoutingEntry::enabled)
                .sorted(Comparator.comparingDouble(RoutingEntry::priority).reversed())
                .toList();
    }

    /**
     * 覆盖写入路由表。
     *
     * @param entries 路由条目列表
     */
    public void saveRouting(List<RoutingEntry> entries) {
        Path file = AppConstants.Evolve.ROUTING_FILE;
        try {
            Files.createDirectories(file.getParent());
            String json = JsonUtils.toPrettyJson(entries != null ? entries : List.of());
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("[RoutingEngine] 保存路由文件失败: {}", file, e);
            throw new RuntimeException("保存路由文件失败", e);
        }
    }

    /**
     * 追加单条路由条目。
     * <p>
     * 添加前校验引用的基因存在，避免出现悬空路由。
     *
     * @param entry 待追加的路由条目
     */
    public void addEntry(RoutingEntry entry) {
        if (entry == null) {
            return;
        }
        if (geneRepository.findById(entry.geneId()) == null) {
            log.warn("[RoutingEngine] 基因不存在，拒绝添加路由: geneId={}", entry.geneId());
            throw new IllegalArgumentException("基因不存在: " + entry.geneId());
        }
        List<RoutingEntry> existing = loadAllEntries();
        existing.add(entry);
        saveRouting(existing);
    }

    /**
     * 加载路由表全部条目（含 disabled），文件不存在或读取失败时返回空列表。
     */
    private List<RoutingEntry> loadAllEntries() {
        Path file = AppConstants.Evolve.ROUTING_FILE;
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(JsonUtils.fromJson(json, new TypeReference<List<RoutingEntry>>() {}));
        } catch (IOException e) {
            log.error("[RoutingEngine] 读取路由文件失败: {}", file, e);
            return new ArrayList<>();
        }
    }
}
