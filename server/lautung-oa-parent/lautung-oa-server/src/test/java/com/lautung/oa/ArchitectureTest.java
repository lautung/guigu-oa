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
 *
 * <pre>
 * common ← framework
 * common, model ← security
 * common, framework, security, model ← auth
 * common, framework, security, model, auth ← process
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
            .layer("Auth").definedBy("com.lautung.oa.auth..")
            .layer("Process").definedBy("com.lautung.oa.process..")
            // 启动类在 com.lautung.oa 包根，demo seeder 在 com.lautung.oa.demo
            .layer("Server").definedBy("com.lautung.oa", "com.lautung.oa.demo..")
            .whereLayer("Common").mayNotAccessAnyLayer()
            .whereLayer("Framework").mayOnlyAccessLayers("Common")
            .whereLayer("Security").mayOnlyAccessLayers("Common", "Model")
            .whereLayer("Model").mayNotAccessAnyLayer()
            .whereLayer("Auth").mayOnlyAccessLayers("Common", "Framework", "Security", "Model")
            .whereLayer("Process").mayOnlyAccessLayers("Common", "Framework", "Security", "Model", "Auth")
            .whereLayer("Server").mayOnlyAccessLayers("Common", "Framework", "Security", "Model", "Auth", "Process")
            .whereLayer("Common").mayOnlyBeAccessedByLayers("Framework", "Security", "Auth", "Process", "Server")
            .whereLayer("Framework").mayOnlyBeAccessedByLayers("Auth", "Process", "Server")
            .whereLayer("Security").mayOnlyBeAccessedByLayers("Auth", "Process", "Server")
            .whereLayer("Model").mayOnlyBeAccessedByLayers("Security", "Auth", "Process", "Server")
            .whereLayer("Auth").mayOnlyBeAccessedByLayers("Process", "Server")
            .whereLayer("Process").mayOnlyBeAccessedByLayers("Server")
            .whereLayer("Server").mayNotBeAccessedByAnyLayer()
            // 新顶层包必须先归入某个层，否则会静默绕过上面的全部边
            .ensureAllClassesAreContainedInArchitecture()
            .allowEmptyShould(true)
            .as("模块依赖边与 #195 冻结的 DAG 一致");

    @ArchTest
    static final ArchRule MODULE_CYCLES = SlicesRuleDefinition.slices()
            .matching("com.lautung.oa.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)
            .as("模块包之间无环");
}
