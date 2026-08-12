package cn.bitloom.project.git;

/**
 * 文件 Git 工作区状态（用于目录树/文件视图着色）。
 */
public enum GitFileStatus {
    /** 已暂存的新增文件（git 状态 A） */
    ADDED,
    /** 已修改的文件（git 状态 M，含暂存/未暂存） */
    MODIFIED,
    /** 未跟踪的新文件（git 状态 ??） */
    UNTRACKED
}
