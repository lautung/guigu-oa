## Parent

Part of #8（实现：云尚办公统一客户端）

## What to build

**配置：审批模板**——三步向导：基本设置（类型、名称、图标、描述）→ 表单设置（可视化设计器）→ 流程设置（Activiti **zip 上传**）→ 提交保存。列表对未发布模板可发布；已发布模板在办事端发起审批可见。

不含 Activiti 在线 iframe 设计器（onlineProcessSet）。

## Acceptance criteria

- [ ] 三步向导可新建模板并保存为草稿（未发布）
- [ ] 未发布模板可编辑、删除
- [ ] zip 上传流程定义成功
- [ ] 列表「发布」后模板 status 变为已发布
- [ ] 已发布模板在办事端「发起审批」可见（可与发起审批票联调）
- [ ] 不含 online iframe 设计器入口

## Blocked by

- #19 配置：审批类型
