# 用户名唯一由 service 查重与单列唯一索引共同保证且删除让位

用户管理（配置）此前对重名用户不做任何防线：`sys_user.username` 只有普通索引（V1），save/update 直接落库。重名的危害是硬的——登录链路 `getUserByUserName` 走 MyBatis-Plus `selectOne`，多行抛 `TooManyResultsException`，被 `DaoAuthenticationProvider` 包成 `InternalAuthenticationServiceException` 后落到 `TokenLoginFilter.unsuccessfulAuthentication` 默认分支，该用户名此后每次登录得到 **401+208 通用文案**（与「密码错误」不可区分，无从察觉库里有重名）；同一个查找还被 `ApprovalWriter` 用来按用户名解析审批人，重名会直接打断发起/审批写入。这是 #160 决议采纳项（#159 筛选、#171 承接）：防重复提交横切被拒，save 族缺口里只有 username 值得动。

决定：三层防线——service 查重先行、单列唯一索引兜底、删除让位。

- **service 判定**：`SysUserServiceImpl` 覆写 `save`/`updateById`，写前按 `username` 查活动行（`@TableLogic` 自动过滤已删行），命中抛 `GuiguException(201, "用户名已存在")`——领域/状态判定归 service（ADR 0033/0040 家族），不加新业务码。update 仅在 `username` 非空时查、排除自身 id；并发窗口漏网的写入由唯一索引拒绝，service 捕获 `DuplicateKeyException` 翻译成同一命名文案（先例：ADR 0032 的 `OaProcessServiceImpl`）。直接调用方一并受保护。
- **唯一索引**：`uk_sys_user_username`（V106），落 SQL 迁移（下一个版本号按 `db/migration` 与 `service-oa/src/main/java/db/migration` 两目录一起看）。客户端零改动：201 文案原样 toast。
- **删除让位**：软删行对一切查询不可见但仍占唯一索引。`removeById` 在同事务里先置空该行 `username` 再软删；V106 同步清空存量已删行的名。新不变量：**已删行不占名**。复合索引 `(username, is_deleted)` 被否决——它只容忍一次复用，第二次软删同名用户就违约（两行 `(zhangsan,1)`），删除直接失败。

存量处置：V106 若遇两个 `is_deleted=0` 的同名活动行，索引创建以 `Duplicate entry` 指名失败、启动中止——哪一行是真身无法自动裁决，拒绝静默改写登录身份；手工改掉或置空多余行后重启。处置法写在迁移注释里。

## 事实修正（对照 #171 的原始主张）

- 重名登录不是 500：异常收口在认证失败处理器，401+208 通用文案。
- 重名登录不消耗锁定额：`TokenLoginFilter` 明确不把 `InternalAuthenticationServiceException` 计入 `LoginAttemptLimiter`（lookup 不是密码猜测），限制器零改动——根因消除后该交互自然消解，不另钉测试。

## 考虑过的替代方案

- **只在 service 查重、不加索引**：并发 save 同名仍会落成重名，硬危害原样保留；索引是唯一的真兜底。
- **只加索引、service 捕获 `DuplicateKeyException` 当唯一防线**：常见路径的拒绝形状要靠异常翻译兜出来，且查重语义散在异常处理里；命名拒绝本就是 ADR 0040 选定的家族形状。
- **复合索引 `(username, is_deleted)`**：见上，两次软删即违约，删除路径直接 500。
- **单列唯一、删除不让位**：被删用户名永久占位，占位行对管理员不可见——重建同名得到「用户名已存在」却查无此人，是不可诊断的维护陷阱。
- **存量活重名自动改写**：保留最小 id、其余改名/置空，启动永不卡；但静默改掉某人的登录身份与「明确拒绝优于静默变通」相悖，且无法自动裁决真身。

## 后果

- 行为变化：重名 save/update 从「静默落库、登录此后每次 208」变为 201 命名拒绝；删除过的用户名可复用；被删行 `username` 为 NULL（对读者不可见，仅索引语义）。
- 顺序契约不变：查重判定在配置 gate 之后，未授予 + 重名 = 205（ADR 0034 顺序的延伸）；静态形状校验（必填/越界）仍在 gate 之前。
- 登录与 `ApprovalWriter` 的 `getUserByUserName` 回到「至多一行」前提，调用方零改动。
- 未动：其他 save 族（role_code / menu path / 模板名重复只是配置噪音，#160 决议明确不动）、防重复提交横切（#160 已拒）、登录失败限制器。
- 回归锚点：`SysUserWriteContractTest`（重名 201 文案、update 撞名/自名、删除让位 + 被删行 username 为 NULL、未授予 205）、`SysUserServiceImplTest`（`DuplicateKeyException` 翻译、不改名不查重）、接口套件 `b-user.spec.ts` B24–B26。
