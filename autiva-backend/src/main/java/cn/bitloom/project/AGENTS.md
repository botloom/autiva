# Project 包

## 概述
本包实现了项目管理系统的完整后端 API，包括项目、需求、设计方案、测试用例、Bug 和通知的 CRUD 及业务流程操作。使用 Spring WebFlux + R2DBC 实现全响应式架构。

## 子包结构

### entity
数据库实体类，使用 Spring Data R2DBC 注解映射数据库表。

| 类名 | 表名 | 说明 |
|------|------|------|
| ProjectEntity | projects | 项目实体 |
| RequirementEntity | requirements | 需求实体 |
| DesignProposalEntity | design_proposals | 设计方案实体 |
| TestCaseEntity | test_cases | 测试用例实体 |
| BugEntity | bugs | Bug实体 |
| NotificationEntity | notifications | 通知实体 |

### repository
响应式数据访问层，继承 R2dbcRepository。

| 接口名 | 自定义查询方法 |
|--------|---------------|
| ProjectRepository | findByName, findByOwnerId |
| RequirementRepository | findByProjectId, findByProjectIdAndStatus |
| DesignProposalRepository | findByProjectId |
| TestCaseRepository | findByProjectId |
| BugRepository | findByProjectId, findByProjectIdAndStatus, findByAssigneeId |
| NotificationRepository | findByStatus, findByTargetClientIdAndStatus |

### service
业务逻辑层，使用 @RequiredArgsConstructor 注入依赖。

| 类名 | 核心方法 | 说明 |
|------|---------|------|
| ProjectService | CRUD + transitionStatus | 项目状态转换: PLANNING→IN_PROGRESS→REVIEW→COMPLETED→ARCHIVED |
| RequirementService | CRUD + submit/review/approve/reject/startImplementation | 需求提交时自动创建 REQUIREMENT_SUBMITTED 通知 |
| DesignProposalService | CRUD + submit/review | 设计方案提交与审核 |
| TestCaseService | CRUD + submit/review | 测试用例提交与审核 |
| BugService | CRUD + assign/fix/verify/close/reopen | Bug创建(OPEN)时自动创建 BUG_SUBMITTED 通知 |
| NotificationService | findPending/send/acknowledge + sendAllPending | 通过 ClientConnectionManager 推送 WebSocket 通知 |

### controller
REST API 控制器，返回 Mono/Flux 响应式类型。

**ProjectController** (`/api/projects`):
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/projects` | 创建项目 |
| GET | `/api/projects` | 查询所有项目 |
| GET | `/api/projects/{id}` | 查询项目详情 |
| GET | `/api/projects/owner/{ownerId}` | 按所有者查询 |
| PUT | `/api/projects/{id}` | 更新项目 |
| PUT | `/api/projects/{id}/status?status=` | 状态转换 |
| DELETE | `/api/projects/{id}` | 删除项目 |

**RequirementController** (`/api/projects/{id}/requirements` + `/api/requirements/{id}`):
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/projects/{projectId}/requirements` | 创建需求 |
| GET | `/api/projects/{projectId}/requirements?status=` | 查询项目需求 |
| GET | `/api/requirements/{id}` | 查询需求详情 |
| PUT | `/api/requirements/{id}` | 更新需求 |
| POST | `/api/requirements/{id}/submit` | 提交需求 |
| POST | `/api/requirements/{id}/review?reviewerId=&comment=` | 审核需求 |
| POST | `/api/requirements/{id}/approve?reviewerId=&comment=` | 批准需求 |
| POST | `/api/requirements/{id}/reject?reviewerId=&comment=` | 驳回需求 |
| POST | `/api/requirements/{id}/start-implementation` | 开始实现 |
| DELETE | `/api/requirements/{id}` | 删除需求 |

**DesignProposalController** (`/api/projects/{id}/design-proposals` + `/api/design-proposals/{id}`):
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/projects/{projectId}/design-proposals` | 创建设计方案 |
| GET | `/api/projects/{projectId}/design-proposals` | 查询项目设计方案 |
| GET | `/api/design-proposals/{id}` | 查询详情 |
| PUT | `/api/design-proposals/{id}` | 更新 |
| POST | `/api/design-proposals/{id}/submit` | 提交 |
| POST | `/api/design-proposals/{id}/review?reviewerId=&comment=&approved=` | 审核 |
| DELETE | `/api/design-proposals/{id}` | 删除 |

**TestCaseController** (`/api/projects/{id}/test-cases` + `/api/test-cases/{id}`):
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/projects/{projectId}/test-cases` | 创建测试用例 |
| GET | `/api/projects/{projectId}/test-cases` | 查询项目测试用例 |
| GET | `/api/test-cases/{id}` | 查询详情 |
| PUT | `/api/test-cases/{id}` | 更新 |
| POST | `/api/test-cases/{id}/submit` | 提交 |
| POST | `/api/test-cases/{id}/review?reviewerId=&comment=&approved=` | 审核 |
| DELETE | `/api/test-cases/{id}` | 删除 |

**BugController** (`/api/projects/{id}/bugs` + `/api/bugs/{id}`):
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/projects/{projectId}/bugs` | 创建Bug |
| GET | `/api/projects/{projectId}/bugs?status=` | 查询项目Bug |
| GET | `/api/bugs/{id}` | 查询详情 |
| PUT | `/api/bugs/{id}` | 更新 |
| POST | `/api/bugs/{id}/assign?assigneeId=` | 分配 |
| POST | `/api/bugs/{id}/fix?fixDescription=` | 修复 |
| POST | `/api/bugs/{id}/verify` | 验证 |
| POST | `/api/bugs/{id}/close` | 关闭 |
| POST | `/api/bugs/{id}/reopen` | 重新打开 |
| DELETE | `/api/bugs/{id}` | 删除 |

**NotificationController** (`/api/notifications`):
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notifications?targetClientId=&status=` | 查询通知 |
| POST | `/api/notifications/{id}/acknowledge` | 确认通知 |

## 状态机

### 项目状态
PLANNING → IN_PROGRESS → REVIEW → COMPLETED → ARCHIVED

### 需求状态
DRAFT → SUBMITTED → IN_REVIEW → APPROVED/REJECTED → IMPLEMENTING → DONE

### 设计方案状态
DRAFT → SUBMITTED → IN_REVIEW → APPROVED/REJECTED

### 测试用例状态
DRAFT → SUBMITTED → IN_REVIEW → APPROVED/REJECTED

### Bug状态
OPEN → ASSIGNED → FIXING → FIXED → VERIFIED → CLOSED (可 REOPENED)

### 通知状态
PENDING → SENT → ACKNOWLEDGED

## 通知机制
- 需求提交(SUBMITTED)时自动创建 REQUIREMENT_SUBMITTED 类型通知
- Bug创建(OPEN)时自动创建 BUG_SUBMITTED 类型通知
- NotificationService 通过 ClientConnectionManager 推送 WebSocket 消息给在线客户端
- 通知消息格式为 JSON，包含 type/notificationType/entityType/entityId/projectId/title/content/notificationId

## 注意事项
1. 所有方法返回 Mono 或 Flux，使用响应式编程模式
2. 使用 @RequiredArgsConstructor 进行依赖注入
3. 实体类使用 @Data @Builder @NoArgsConstructor @AllArgsConstructor 和 @Table/@Column 注解
4. 表结构参见 `init/mysql/01_init.sql`
5. Controller 使用 ResponseEntity 提供精确的 HTTP 状态码
