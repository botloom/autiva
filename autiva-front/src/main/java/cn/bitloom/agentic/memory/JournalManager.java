package cn.bitloom.agentic.memory;

import cn.bitloom.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class JournalManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_SUMMARY_LENGTH = 500;

    private final Path journalDir;

    public JournalManager() {
        this.journalDir = AppConstants.Base.WORKSPACE_DIR.resolve("MAIN").resolve("memories").resolve("journal");
        try {
            Files.createDirectories(this.journalDir);
        }
        catch (Exception e) {
            log.error("创建日志目录失败: {}", journalDir, e);
        }
    }

    public String getRecentJournalsSummary(int days) {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            Path journalFile = journalDir.resolve(date.toString() + ".md");
            if (Files.exists(journalFile)) {
                try {
                    String content = Files.readString(journalFile, StandardCharsets.UTF_8);
                    sb.append("## ").append(date).append("\n");
                    sb.append(truncate(content, MAX_SUMMARY_LENGTH));
                    sb.append("\n\n");
                }
                catch (Exception e) {
                    log.error("读取日志文件失败: {}", journalFile, e);
                }
            }
        }
        return sb.toString();
    }

    public void appendFromSession(String sessionId, String sessionSummary) {
        if (sessionSummary == null || sessionSummary.isBlank()) {
            return;
        }

        String today = LocalDate.now().format(DATE_FORMAT);
        Path journalFile = journalDir.resolve(today + ".md");

        try {
            if (!Files.exists(journalFile)) {
                String header = "---\ndate: " + today + "\nagent: MAIN\n---\n\n# " + today + "\n\n";
                Files.writeString(journalFile, header, StandardCharsets.UTF_8);
            }

            String timeNow = LocalDateTime.now().format(TIME_FORMAT);
            String entry = "\n## 会话 " + timeNow + "\n" + sessionSummary + "\n";
            Files.writeString(journalFile, entry, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);

            log.debug("追加日志条目: sessionId={}", sessionId);
        }
        catch (Exception e) {
            log.error("追加日志失败: sessionId={}", sessionId, e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
