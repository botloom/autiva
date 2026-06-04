# 心跳检查清单

## 定期检查
- 检查最近的信号和事件
- 判断是否需要执行进化周期

## 结构化任务
tasks:
  - name: evolution-check
    interval: 6h
    prompt: "请检查最近的信号和事件，判断是否需要执行进化周期。如果有未处理的信号，执行 evolve_run_cycle。"
