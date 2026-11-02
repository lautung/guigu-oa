package com.lautung.oa.security.custom;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Writes BCrypt hashes and accepts historical unsalted MD5 hashes only for transparent upgrades. */
public final class BcryptPasswordEncoderWithLegacyMd5 implements PasswordEncoder {

    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final Pattern LEGACY_MD5_HASH = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (BCRYPT_HASH.matcher(encodedPassword).matches()) {
            return bcrypt.matches(rawPassword, encodedPassword);
        }
        if (!LEGACY_MD5_HASH.matcher(encodedPassword).matches()) {
            return false;
        }

        byte[] expected = HexFormat.of().formatHex(md5(rawPassword.toString().getBytes(Charset.defaultCharset())))
                .getBytes(StandardCharsets.US_ASCII);
        byte[] stored = encodedPassword.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, stored);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (LEGACY_MD5_HASH.matcher(encodedPassword).matches()) {
            return true;
        }
        return BCRYPT_HASH.matcher(encodedPassword).matches() && bcrypt.upgradeEncoding(encodedPassword);
    }

    private byte[] md5(byte[] input) {
        try {
            return MessageDigest.getInstance("MD5").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable for legacy password verification", e);
        }
    }
}
