package com.kuaishou.intelligentanalysisplatform.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AesGcmCredentialEncryptor implements CredentialEncryptor {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final String PREFIX = "v1";

    private final byte[] secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCredentialEncryptor(@Value("${datasource.credential.secret:0123456789abcdef0123456789abcdef}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("datasource credential secret must be 32 bytes");
        }
        this.secret = bytes.clone();
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + ":" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new BaseBusinessException(ErrorCode.DATASOURCE_ENCRYPTION_ERROR, "datasource encryption error", e.getMessage(), null, false);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            String[] parts = ciphertext.split(":");
            if (parts.length != 3 || !PREFIX.equals(parts[0])) {
                throw new BaseBusinessException(ErrorCode.DATASOURCE_INVALID_CREDENTIAL, "invalid datasource credential");
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BaseBusinessException e) {
            throw e;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BaseBusinessException(ErrorCode.DATASOURCE_INVALID_CREDENTIAL, "invalid datasource credential", e.getMessage(), null, false);
        }
    }
}
