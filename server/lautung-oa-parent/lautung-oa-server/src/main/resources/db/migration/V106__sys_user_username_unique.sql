-- 用户名唯一（#171，ADR 0042）：重名会让登录与按名解析审批人的 selectOne 抛多行异常，
-- 该用户名的登录此后只会得到 401+208 通用失败，与「密码错误」不可区分。
-- 已删行不占名：软删行对查询不可见但仍占唯一索引，先清空存量已删行的名（服务端 remove 此后同步让位）。
-- 若存量库存在两个 is_deleted=0 的同名活动行，索引创建将以 Duplicate entry 指名失败、启动中止：
-- 哪一行是真身无法自动裁决，拒绝静默改写登录身份，请手工改掉或置空多余行后重启。
UPDATE sys_user SET username = NULL WHERE is_deleted = 1;
CREATE UNIQUE INDEX uk_sys_user_username ON sys_user(username);
