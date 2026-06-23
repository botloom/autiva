package cn.bitloom.agentic.evolve.repository;

import cn.bitloom.agentic.evolve.config.EvolveConfig;
import cn.bitloom.agentic.evolve.gene.Gene;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class GeneRepository {

    private static final String AUTIVA_BOT = "Autiva Evolver";
    private static final String AUTIVA_EMAIL = "evolver@autiva.dev";

    private final Path repoDir;
    private Git git;

    public GeneRepository(EvolveConfig config) {
        this.repoDir = config.getGenesDir();
        init();
    }

    public synchronized void init() {
        try {
            Files.createDirectories(repoDir);
            if (!Files.exists(repoDir.resolve(".git"))) {
                git = Git.init()
                        .setDirectory(repoDir.toFile())
                        .call();
                log.info("[Evolve] JGit仓库已初始化: {}", repoDir);
            } else {
                git = Git.open(repoDir.toFile());
                log.info("[Evolve] JGit仓库已打开: {}", repoDir);
            }
        } catch (IOException | GitAPIException e) {
            log.error("[Evolve] JGit初始化失败", e);
        }
    }

    public synchronized void commit(Gene gene, String message) {
        if (git == null) {
            log.warn("[Evolve] JGit未初始化，跳过提交");
            return;
        }

        try {
            String genePath = gene.id() + "/";
            git.add()
                    .addFilepattern(genePath)
                    .call();

            PersonIdent author = new PersonIdent(AUTIVA_BOT, AUTIVA_EMAIL);
            git.commit()
                    .setAuthor(author)
                    .setCommitter(author)
                    .setMessage(message)
                    .call();

            log.info("[Evolve] JGit提交成功: {} - {}", gene.id(), message);
        } catch (GitAPIException e) {
            log.error("[Evolve] JGit提交失败: {}", gene.id(), e);
        }
    }

    public List<CommitInfo> history(String geneId) {
        if (git == null) {
            return Collections.emptyList();
        }

        try {
            String path = geneId + "/";
            Iterable<RevCommit> commits = git.log()
                    .addPath(path)
                    .call();

            List<CommitInfo> result = new ArrayList<>();
            for (RevCommit commit : commits) {
                result.add(new CommitInfo(
                        commit.getName().substring(0, 8),
                        commit.getShortMessage(),
                        commit.getAuthorIdent().getName(),
                        commit.getCommitTime() * 1000L
                ));
            }
            return result;
        } catch (GitAPIException e) {
            log.error("[Evolve] JGit历史查询失败: {}", geneId, e);
            return Collections.emptyList();
        }
    }

    public String diff(String geneId, String fromCommit, String toCommit) {
        if (git == null) {
            return "";
        }

        try {
            var outputStream = new java.io.ByteArrayOutputStream();
            git.diff()
                    .setOutputStream(outputStream)
                    .call();
            return outputStream.toString();
        } catch (GitAPIException e) {
            log.error("[Evolve] JGit diff查询失败: {}", geneId, e);
            return "";
        }
    }

    public void revert(String geneId, String commitHash) {
        if (git == null) {
            log.warn("[Evolve] JGit未初始化，无法回滚");
            return;
        }

        try {
            git.checkout()
                    .setStartPoint(commitHash)
                    .addPath(geneId + "/")
                    .call();

            PersonIdent author = new PersonIdent(AUTIVA_BOT, AUTIVA_EMAIL);
            git.commit()
                    .setAuthor(author)
                    .setCommitter(author)
                    .setMessage("revert " + geneId + " to " + commitHash)
                    .call();

            log.info("[Evolve] JGit回滚成功: {} -> {}", geneId, commitHash);
        } catch (GitAPIException e) {
            log.error("[Evolve] JGit回滚失败: {} -> {}", geneId, commitHash, e);
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (git != null) {
            git.close();
            log.info("[Evolve] JGit仓库已关闭");
        }
    }

    public record CommitInfo(
            String hash,
            String message,
            String author,
            long timestamp
    ) {}
}
