package com.atguigu.security.custom;

public final class AccountStatus {

    private AccountStatus() {
    }

    public static boolean isEnabled(Integer status) {
        return status != null && status != 0;
    }
}
