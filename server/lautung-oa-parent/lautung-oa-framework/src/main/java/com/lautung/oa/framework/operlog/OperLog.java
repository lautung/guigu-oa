package com.lautung.oa.framework.operlog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后台写动作的操作日志声明（ADR 0054）。纯元数据：注解在 framework，切面与落库在 audit，
 * 所以这里刻意不引任何新依赖边，任何域模块都能挂。
 *
 * <p>{@code title} 用「模块·动作」形状（如「用户管理·新增用户」），写入时快照进日志行；
 * 菜单或模块改名不回写历史行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 操作名称，形状为「模块·动作」。 */
    String title();

    /** 业务类型；落库为 {@link BusinessType#getCode()}。 */
    BusinessType businessType();
}
