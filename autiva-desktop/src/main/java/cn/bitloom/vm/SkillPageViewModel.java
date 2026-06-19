package cn.bitloom.vm;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.agentic.skill.SkillManager;
import cn.bitloom.util.ExecutorManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPageViewModel {

    private final SkillManager skillManager;

    @Getter
    private final ObservableList<Skill> skills = FXCollections.observableArrayList();

    public void loadSkills() {
        skills.setAll(skillManager.getAllSkills());
    }

    public void loadSkillsAsync(Runnable onLoaded) {
        Task<List<Skill>> task = new Task<>() {
            @Override
            protected List<Skill> call() {
                return skillManager.getAllSkills();
            }
        };
        task.setOnSucceeded(e -> {
            skills.setAll(task.getValue());
            if (onLoaded != null) onLoaded.run();
        });
        task.setOnFailed(e -> log.error("加载技能列表失败", task.getException()));
        ExecutorManager.getPlatformTaskExecutor().execute(task);
    }

    public void importSkillFromZip(Path zipPath) throws IOException {
        skillManager.importSkillFromZip(zipPath);
        skillManager.loadSkills();
        loadSkills();
    }

    public void importSkillFromZipAsync(Path zipPath, Runnable onLoaded) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                skillManager.importSkillFromZip(zipPath);
                skillManager.loadSkills();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            skills.setAll(skillManager.getAllSkills());
            if (onLoaded != null) onLoaded.run();
        });
        task.setOnFailed(e -> log.error("导入技能失败", task.getException()));
        ExecutorManager.getPlatformTaskExecutor().execute(task);
    }

    public void deleteSkill(String name) {
        skillManager.deleteSkill(name);
        skillManager.loadSkills();
        loadSkills();
    }
}
