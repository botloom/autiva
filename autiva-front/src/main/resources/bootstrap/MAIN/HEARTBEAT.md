# 心跳检查清单

## 定期检查
- 检查是否有待处理的任务或提醒
- 回顾最近的对话，是否有需要跟进的事项
- 检查记忆系统是否有重复或过时的记忆需要整合

## 结构化任务
tasks:
  - name: memory-consolidation
    interval: 6h
    prompt: "检查记忆系统是否有重复或过时的记忆需要合并/清理，更新 MEMORY_CORE.md，确保记忆索引是最新的"
  - name: daily-journal
    interval: 24h
    prompt: "整理今天的对话日志：1) 回顾今天的对话 2) 将重要信息保存为记忆 3) 更新核心记忆"
