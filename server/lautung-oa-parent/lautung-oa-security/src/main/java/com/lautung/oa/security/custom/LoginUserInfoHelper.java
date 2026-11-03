package com.lautung.oa.security.custom;

public class LoginUserInfoHelper {

    private static ThreadLocal<Long> userId = new ThreadLocal<Long>();
    private static ThreadLocal<String> username = new ThreadLocal<String>();
    private static ThreadLocal<String> sessionId = new ThreadLocal<String>();

    public static void setUserId(Long _userId) {
        userId.set(_userId);
    }
    public static Long getUserId() {
        return userId.get();
    }
    public static void removeUserId() {
        userId.remove();
    }
    public static void setUsername(String _username) {
        username.set(_username);
    }
    public static String getUsername() {
        return username.get();
    }
    public static void removeUsername() {
        username.remove();
    }
    /** 当前请求的会话 id（ADR 0060）：/info 与在线用户列表的「当前会话」标记都读它。 */
    public static void setSessionId(String _sessionId) {
        sessionId.set(_sessionId);
    }
    public static String getSessionId() {
        return sessionId.get();
    }
    public static void removeSessionId() {
        sessionId.remove();
    }
}
