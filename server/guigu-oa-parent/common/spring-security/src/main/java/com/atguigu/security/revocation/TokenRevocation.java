package com.atguigu.security.revocation;

/** Remembers turned-in session ids so their access and refresh tokens stop authenticating. */
public interface TokenRevocation {

    boolean isRevoked(String sessionId);

    void revoke(String sessionId);
}
