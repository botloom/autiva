package cn.bitloom.agentic.evolve.experience;

/**
 * 经验状态枚举。
 * <p>
 * 用于标记一条经验的成熟度阶段：
 * <ul>
 *   <li>{@link #CANDIDATE} 候选 — 支持轨迹数不足，尚未达到验证门槛</li>
 *   <li>{@link #VERIFIED} 已验证 — 支持轨迹数达到门槛，可被引用</li>
 *   <li>{@link #DEPRECATED} 已弃用 — 经验过时或被证伪，不再推荐</li>
 * </ul>
 */
public enum ExperienceStatus {
    /** 候选：支持轨迹数不足，尚未达到验证门槛 */
    CANDIDATE,
    /** 已验证：支持轨迹数达到门槛，可被引用 */
    VERIFIED,
    /** 已弃用：经验过时或被证伪，不再推荐 */
    DEPRECATED
}
