package cn.bitloom.agentic.memory;

import java.io.IOException;
import java.util.List;

/**
 * 智能体长期记忆存储抽象。
 * <p>
 * 解耦记忆工具与具体存储后端（文件系统/JDBC/Redis 等）。
 * 所有路径均为相对路径（相对于记忆根目录），由实现负责路径解析与安全校验。
 *
 * @see FileSystemAgentMemoryStore 文件系统默认实现
 */
public interface AgentMemoryStore {

    /**
     * 目录条目。
     *
     * @param name      条目名称
     * @param directory 是否为目录
     * @param size      字节数（目录为 -1）
     */
    record Entry(String name, boolean directory, long size) {}

    /**
     * 读取文件完整内容。
     *
     * @param relativePath 相对路径
     * @return 文件文本内容
     * @throws IOException         读取失败
     * @throws SecurityException   路径不安全（遍历/绝对路径）
     */
    String readFile(String relativePath) throws IOException;

    /**
     * 按行读取文件内容。
     *
     * @param relativePath 相对路径
     * @return 行列表（1-based 语义由调用方处理）
     * @throws IOException         读取失败
     * @throws SecurityException   路径不安全
     */
    List<String> readLines(String relativePath) throws IOException;

    /**
     * 覆盖写入文件（父目录不存在自动创建）。
     *
     * @param relativePath 相对路径
     * @param content      文本内容
     * @throws IOException         写入失败
     * @throws SecurityException   路径不安全
     */
    void writeFile(String relativePath, String content) throws IOException;

    /**
     * 创建新文件（必须不存在）。
     *
     * @param relativePath 相对路径
     * @param content      文本内容
     * @throws IOException         写入失败或文件已存在
     * @throws SecurityException   路径不安全
     */
    void createFile(String relativePath, String content) throws IOException;

    /**
     * 删除文件或目录（递归）。
     *
     * @param relativePath 相对路径
     * @throws IOException         删除失败
     * @throws SecurityException   路径不安全
     */
    void delete(String relativePath) throws IOException;

    /**
     * 移动/重命名文件或目录。
     *
     * @param oldPath 源相对路径
     * @param newPath 目标相对路径
     * @throws IOException         移动失败
     * @throws SecurityException   路径不安全
     */
    void move(String oldPath, String newPath) throws IOException;

    /**
     * 路径是否存在。
     */
    boolean exists(String relativePath);

    /**
     * 路径是否为目录。
     */
    boolean isDirectory(String relativePath);

    /**
     * 文件字节数（目录返回 -1）。
     */
    long size(String relativePath) throws IOException;

    /**
     * 列出目录一层内容（非递归）。
     *
     * @param relativePath 目录相对路径
     * @return 条目列表
     * @throws IOException         列出失败
     * @throws SecurityException   路径不安全
     */
    List<Entry> list(String relativePath) throws IOException;

    /**
     * 初始化存储（如创建根目录），幂等。
     */
    void init() throws IOException;
}
