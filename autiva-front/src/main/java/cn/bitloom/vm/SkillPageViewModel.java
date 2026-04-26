package cn.bitloom.vm;

import cn.bitloom.agentic.skill.Skill;
import cn.bitloom.agentic.skill.SkillManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

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

    public Skill importSkillFromZip(Path zipPath) throws IOException {
        Skill importedSkill = skillManager.importSkillFromZip(zipPath);
        skillManager.loadSkills();
        loadSkills();
        return importedSkill;
    }

    public void deleteSkill(String name) {
        skillManager.deleteSkill(name);
        skillManager.loadSkills();
        loadSkills();
    }
}
