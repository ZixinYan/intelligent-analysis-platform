package com.kuaishou.intelligentanalysisplatform.infra.security;

public interface CredentialEncryptor {
    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
