package com.lautung.oa;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 冻结 #195 的模块依赖边。包根即模块边界，只约束生产类：测试类跨模块引用是装配模块
 * （{@code lautung-oa-server}）的合法用法，因此用 {@link ImportOption.DoNotIncludeTests} 排除。
 * 新增模块必须同时声明为 {@code lautung-oa-server} 的依赖，否则不在本测试的分析集内。
 *
 * <pre>
 * common ← framework
 * common, model ← security
 * common ← storage
 * common, framework, security, model ← audit
 * common, framework, security, model, storage ← auth
 * common, framework, security, model, storage ← notice
 * common, framework, security, model, auth, storage ← process
 * common, framework, security, model ← monitor
 * common, framework, security, model ← dict
 * common, framework, security, model ← config
 * common, framework, security, model, config ← auth
 * common, framework, security, model ← attendance（ADR 0073：配置/打卡/账本）
 * common, framework, security, model, process ← attendance（#282：请假/加班单经业务审批端口）
 * 全模块 ← server
 * </pre>
 */
@AnalyzeClasses(packages = "com.lautung.oa", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule MODULE_DEPENDENCY_EDGES = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Common").definedBy("com.lautung.oa.common..")
            .layer("Framework").definedBy("com.lautung.oa.framework..")
            .layer("Security").definedBy("com.lautung.oa.security..")
            // vo 是 model 模块里的请求 DTO 包，与 model 同属数据层
            .layer("Model").definedBy("com.lautung.oa.model..", "com.lautung.oa.vo..")
            // 私有对象存储的共享缝（#218）：只接 common，不依赖任何域模块
            .layer("Storage").definedBy("com.lautung.oa.storage..")
            .layer("Audit").definedBy("com.lautung.oa.audit..")
            .layer("Auth").definedBy("com.lautung.oa.auth..")
            .layer("Notice").definedBy("com.lautung.oa.notice..")
            .layer("Process").definedBy("com.lautung.oa.process..")
            // 系统监控（ADR 0060）：只调用 security 的会话端口与自己的只读 Mapper
            .layer("Monitor").definedBy("com.lautung.oa.monitor..")
            // 字典模块（ADR 0062）：只访问 common/framework/security/model，向上只给 server
            .layer("Dict").definedBy("com.lautung.oa.dict..")
            // 参数模块（ADR 0062）：只访问 common/framework/security/model，向上只给 auth 端口
            .layer("Config").definedBy("com.lautung.oa.config..")
            // 考勤模块（ADR 0073）：配置面只访问 common/framework/security/model；打卡/单据等
            // 后续票再扩 process 边。向上只给 server。
            .layer("Attendance").definedBy("com.lautung.oa.attendance..")
            // 启动类在 com.lautung.oa 包根，demo seeder 在 com.lautung.oa.demo
            .layer("Server").definedBy("com.lautung.oa", "com.lautung.oa.demo..")
            .whereLayer("Common").mayNotAccessAnyLayer()
            .whereLayer("Framework").mayOnlyAccessLayers("Common")
            .whereLayer("Security").mayOnlyAccessLayers("Common", "Model")
            .whereLayer("Model").mayNotAccessAnyLayer()
            .whereLayer("Storage").mayOnlyAccessLayers("Common")
            // 审计只接 framework/model/security 的端口，不依赖任何域模块，所以登录/业务路径不动（ADR 0054）
            .whereLayer("Audit").mayOnlyAccessLayers("Common", "Framework", "Security", "Model")
            // config ← auth（ADR 0062）：注册开关等由 auth 注入只读端口，auth 不直连 sys_config；
            // auth → storage（#254）：个人中心头像复用私有对象存储。
            .whereLayer("Auth").mayOnlyAccessLayers(
                    "Common", "Framework", "Security", "Model", "Config", "Storage")
            .whereLayer("Notice").mayOnlyAccessLayers("Common", "Framework", "Security", "Model", "Storage")
            .whereLayer("Process").mayOnlyAccessLayers(
                    "Common", "Framework", "Security", "Model", "Auth", "Storage")
            .whereLayer("Monitor").mayOnlyAccessLayers("Common", "Framework", "Security", "Model")
            .whereLayer("Dict").mayOnlyAccessLayers("Common", "Framework", "Security", "Model")
            .whereLayer("Config").mayOnlyAccessLayers("Common", "Framework", "Security", "Model")
            // 考勤单据（#282，ADR 0067/0073）：只经 process 的业务审批端口发起与回写。
            .whereLayer("Attendance").mayOnlyAccessLayers(
                    "Common", "Framework", "Security", "Model", "Process")
            .whereLayer("Server").mayOnlyAccessLayers(
                    "Common", "Framework", "Security", "Model", "Storage", "Audit", "Auth", "Notice",
                    "Process", "Monitor", "Dict", "Config", "Attendance")
            .whereLayer("Common").mayOnlyBeAccessedByLayers(
                    "Framework", "Security", "Storage", "Audit", "Auth", "Notice", "Process", "Monitor",
                    "Dict", "Config", "Attendance", "Server")
            .whereLayer("Framework").mayOnlyBeAccessedByLayers(
                    "Audit", "Auth", "Notice", "Process", "Monitor", "Dict", "Config", "Attendance",
                    "Server")
            .whereLayer("Security").mayOnlyBeAccessedByLayers(
                    "Audit", "Auth", "Notice", "Process", "Monitor", "Dict", "Config", "Attendance",
                    "Server")
            .whereLayer("Model").mayOnlyBeAccessedByLayers(
                    "Security", "Audit", "Auth", "Notice", "Process", "Monitor", "Dict", "Config",
                    "Attendance", "Server")
            .whereLayer("Storage").mayOnlyBeAccessedByLayers("Auth", "Notice", "Process", "Server")
            .whereLayer("Audit").mayOnlyBeAccessedByLayers("Server")
            .whereLayer("Auth").mayOnlyBeAccessedByLayers("Process", "Server")
            .whereLayer("Notice").mayOnlyBeAccessedByLayers("Server")
            .whereLayer("Process").mayOnlyBeAccessedByLayers("Attendance", "Server")
            .whereLayer("Monitor").mayOnlyBeAccessedByLayers("Server")
            .whereLayer("Dict").mayOnlyBeAccessedByLayers("Server")
            .whereLayer("Config").mayOnlyBeAccessedByLayers("Auth", "Server")
            .whereLayer("Attendance").mayOnlyBeAccessedByLayers("Server")
            .whereLayer("Server").mayNotBeAccessedByAnyLayer()
            // 新顶层包必须先归入某个层，否则会静默绕过上面的全部边
            .ensureAllClassesAreContainedInArchitecture()
            .as("模块依赖边与 #195 冻结的 DAG 一致");

    @ArchTest
    static final ArchRule MODULE_CYCLES = SlicesRuleDefinition.slices()
            .matching("com.lautung.oa.(*)..")
            .should().beFreeOfCycles()
            .as("模块包之间无环");
}
