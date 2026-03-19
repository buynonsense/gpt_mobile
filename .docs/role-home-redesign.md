# 角色页重构任务文档

## 背景
- 当前首页是会话页，主对象是 `ChatRoom`
- 当前“面具”能力通过 `AiMask` 提供，主要用于预设 system prompt
- 用户希望首页改成“角色页”，主对象从“会话”切换为“角色”

## 本次重构目标
1. 首页从“会话列表”改成“角色列表”
2. 角色基于现有 `AiMask` 升级，不再只是附属能力
3. 点击角色后直接进入该角色最近会话，没有则自动创建
4. 用户不再显式新建会话
5. 首页支持按分组展示角色，布局为 3 x n
6. 支持全局模糊搜索历史消息内容
7. 角色删除语义统一改成“归档”

## 产品规则

### 角色
- 角色本质上是升级后的 `AiMask`
- 每个角色包含：
  - 名称
  - system prompt
  - 分组名
  - 默认角色标记
  - 归档标记

### 默认角色
- 系统预置默认角色：`AI助手`
- 特征：
  - 无 system prompt
  - 永远存在
  - 作为普通通用聊天入口

### 会话
- 会话仍使用现有 `ChatRoom`
- 语义变成“某个角色下的一段历史聊天”
- 第一阶段继续沿用 `maskId` 字段表达角色归属，不做数据库列重命名

### 首页行为
- 首页展示角色，而不是会话
- 点击角色：
  - 若该角色存在最近未归档会话，直接进入
  - 若没有，则自动创建一个新的会话再进入
- 首页移除“新建会话”入口

### 角色分组
- 第一阶段采用简单文本分组：`groupName`
- 首页按组展示，组内使用 3 列网格布局

### 搜索
- 搜索目标不是角色名，而是所有历史消息内容
- 首页提供全局搜索入口
- 搜索结果点击后直接进入对应会话

### 归档
- 主角色管理页中的“删除角色”操作统一改成“归档角色”
- 归档角色时：
  - 角色进入归档
  - 该角色下所有历史会话一起进入归档
- 真正删除只在归档页中进行

## 数据层设计

### AiMask 升级字段
在现有 `AiMask` 基础上增加：
- `groupName: String`
- `isDefault: Boolean`
- `isArchived: Boolean`

### ChatRoom 升级字段
在现有 `ChatRoom` 基础上增加：
- `isArchived: Boolean`

### 数据迁移建议
- 数据库版本从 `3` 升级到 `4`
- 新增 `MIGRATION_3_4`

#### 对 `ai_masks`
- 新增字段：
  - `group_name TEXT NOT NULL DEFAULT '未分组'`
  - `is_default INTEGER NOT NULL DEFAULT 0`
  - `is_archived INTEGER NOT NULL DEFAULT 0`
- 若默认角色不存在，则插入：
  - `name = 'AI助手'`
  - `system_prompt = ''`
  - `group_name = '默认'`
  - `is_default = 1`
  - `is_archived = 0`

#### 对 `chats`
- 新增字段：
  - `is_archived INTEGER NOT NULL DEFAULT 0`

### 旧数据兼容策略
- 旧会话若 `maskId != null`：继续归到对应角色
- 旧会话若 `maskId == null`：业务上视为属于默认角色 `AI助手`

## 受影响模块

### 数据层
- `data/database/entity/AiMask.kt`
- `data/database/entity/ChatRoom.kt`
- `data/database/dao/AiMaskDao.kt`
- `data/database/dao/ChatRoomDao.kt`
- `data/database/dao/MessageDao.kt`
- `data/repository/AiMaskRepository.kt`
- `data/repository/AiMaskRepositoryImpl.kt`
- `data/repository/ChatRepository.kt`
- `data/repository/ChatRepositoryImpl.kt`
- `data/database/ChatDatabase.kt`
- `data/database/Migrations.kt`

### 首页与导航
- `presentation/ui/home/HomeScreen.kt`
- `presentation/ui/home/HomeViewModel.kt`
- `presentation/common/NavigationGraph.kt`
- `presentation/common/Route.kt`

### 角色管理
- `presentation/ui/mask/AiMaskListScreen.kt`
- `presentation/ui/mask/AiMaskListViewModel.kt`

### 聊天页
- `presentation/ui/chat/ChatViewModel.kt`
- 如有必要，少量调整 `ChatScreen.kt`

## 首页 UI 方案

### 顶栏
- 标题：`角色`
- 左侧：搜索入口
- 右侧：设置入口

### 主体
- 按分组展示角色 section
- 每组为 3 列角色卡片
- 默认角色 `AI助手` 置于默认分组中

### 卡片内容
- 角色名
- 简短摘要（可取 system prompt 前若干字）
- 可选显示最近使用信息

### 操作入口
- 移除“新建会话” FAB
- 改为“创建角色”入口

## 搜索页方案
- 新增独立搜索页
- 通过消息内容模糊匹配返回结果
- 结果项展示：
  - 命中消息片段
  - 所属角色
  - 所属会话标题
  - 时间
- 点击结果进入对应 `ChatRoom`

## 归档页方案
- 展示已归档角色
- 同时体现该角色下归档会话数量
- 支持：
  - 恢复角色（连带恢复会话）
  - 永久删除角色（连带永久删除会话）

## 实现边界

### 第一阶段必须做
- 数据库迁移到 v4
- 默认角色 `AI助手`
- 首页角色化
- 角色分组 3 列布局
- 点击角色直进最近会话 / 自动创建会话
- 角色管理页支持分组字段
- 角色归档
- 全局消息搜索页

### 第一阶段暂不做
- 角色封面图/头像
- 角色内独立会话列表页
- 搜索结果定位到具体消息位置
- 分组排序/拖拽
- 高级归档筛选器

## 推荐实施顺序
1. 数据模型与数据库迁移
2. Repository / DAO 扩展
3. 首页状态层改造
4. 首页 UI 改角色页
5. 角色管理页升级
6. 搜索页
7. 归档页
