package com.atguigu.security.response;

import com.atguigu.common.result.ResultCodeEnum;

/**
 * 认证家族失败（#145 起 HTTP 401 + 业务码；ADR 0037 续期同族）：只有两种——
 * 凭证无效（208）与账号停用（209），对应 GLOSSARY「会话核验」的两条出口。
 */
public enum AuthenticationFailure {
    INVALID_TOKEN(ResultCodeEnum.LOGIN_ERROR),
    ACCOUNT_DISABLED(ResultCodeEnum.ACCOUNT_DISABLED);

    private final ResultCodeEnum code;

    AuthenticationFailure(ResultCodeEnum code) {
        this.code = code;
    }

    public ResultCodeEnum code() {
        return code;
    }
}
