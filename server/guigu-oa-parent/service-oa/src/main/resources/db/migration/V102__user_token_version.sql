-- 改密作废该用户全部会话（#143）：令牌带 token_version，改密在同一行里加一。
-- 旧令牌的 claim 缺省为 0；已有行补 0 后继续有效，直到该用户下一次改密。
ALTER TABLE sys_user ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
