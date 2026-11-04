package com.lautung.oa.config.service;

/**
 * 参数的只读端口（#252，ADR 0062）：下游（#253 的注册开关等）按接口取值，
 * 不直连 {@code sys_config}。实现由 config 模块供给，读路径走 {@code config:} 前缀缓存。
 */
public interface ConfigReader {

    /** 参数值；参数不存在或没有值时返回 null。 */
    String getValue(String configKey);

    /** 布尔参数；参数不存在、没有值或值无法识别时返回 {@code defaultValue}。 */
    boolean getBoolean(String configKey, boolean defaultValue);
}
