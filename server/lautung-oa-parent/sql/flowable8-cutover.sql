-- Flowable 8 切库：硬前提闸门与引擎表 DROP（不执行业务表变更）
-- 推荐用同目录 flowable8-cutover.sh；本文件供对照或手工演练。
-- 规格：docs/spec/升级到Spring-Boot-4与Vue-3的依赖规格.md §6
--
-- 禁止：改 ACT_GE_PROPERTY.schema.version 骗升级；把审批中行改成 -1；删除 oa_* / sys_*。

-- 1. 硬前提：oa_process.status=1 必须为 0。否则停止。
SELECT COUNT(*) AS in_progress
FROM `oa_process`
WHERE `status` = 1 AND `is_deleted` = 0;

-- 2. 将要 DROP 的引擎表（ACT_% 含 Activiti 独有表，以及 Flowable 的 FLW_%）。
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND (TABLE_NAME LIKE 'ACT\_%' ESCAPE '\\' OR TABLE_NAME LIKE 'FLW\_%' ESCAPE '\\')
ORDER BY TABLE_NAME;

-- 3. DROP 示例（实际表名以第 2 步为准；脚本会动态生成）：
-- SET FOREIGN_KEY_CHECKS = 0;
-- DROP TABLE IF EXISTS `ACT_RU_INTEGRATION`;
-- DROP TABLE IF EXISTS `ACT_RU_TASK`;
-- DROP TABLE IF EXISTS `FLW_RU_BATCH`;
-- SET FOREIGN_KEY_CHECKS = 1;

-- 4. 切库后以 Flowable 8 启动（database-schema-update=true, check-process-definitions=false）
--    再核对引擎表已重建：
-- SHOW TABLES LIKE 'ACT\_%';
-- SHOW TABLES LIKE 'FLW\_%';
