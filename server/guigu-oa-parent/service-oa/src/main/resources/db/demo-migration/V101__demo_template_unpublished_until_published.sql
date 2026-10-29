-- 演示模板的发布证据只能由发布动作产生。旧版把模板直接种成已发布却没有发布记录，
-- 这里把它退回未发布，交给 DemoScenarioSeeder 走正常发布流程补齐产物、发布记录与引擎部署。
-- 已带发布记录的模板不受影响（发布冻结不许猜测原内容）。
UPDATE oa_process_template
SET status = 0, update_time = NOW()
WHERE process_definition_key = 'demoApproval'
  AND status = 1
  AND is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM oa_process_template_publication p
    WHERE p.process_template_id = oa_process_template.id
  );
