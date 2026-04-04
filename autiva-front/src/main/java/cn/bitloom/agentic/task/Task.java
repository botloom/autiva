package cn.bitloom.agentic.task;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class Task {

    private Long id;
    private String subject;
    private String description;
    private String status = "pending";
    private List<Long> blockedBy = new ArrayList<>();
    private List<Long> blocks = new ArrayList<>();
    private String owner = "";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
