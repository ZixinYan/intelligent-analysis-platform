package com.kuaishou.intelligentanalysisplatform.infra.security;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmCredentialEncryptorTest {

    @Test
    void shouldEncryptAndDecrypt() {
        AesGcmCredentialEncryptor encryptor = new AesGcmCredentialEncryptor("0123456789abcdef0123456789abcdef");
        String encrypted = encryptor.encrypt("secret");
        assertNotEquals("secret", encrypted);
        assertEquals("secret", encryptor.decrypt(encrypted));
    }

    @Test
    void shouldGenerateDifferentCiphertextForSamePlaintext() {
        AesGcmCredentialEncryptor encryptor = new AesGcmCredentialEncryptor("0123456789abcdef0123456789abcdef");
        String first = encryptor.encrypt("secret");
        String second = encryptor.encrypt("secret");
        assertNotEquals(first, second);
    }

    @Test
    void shouldRejectInvalidCiphertext() {
        AesGcmCredentialEncryptor encryptor = new AesGcmCredentialEncryptor("0123456789abcdef0123456789abcdef");
        assertThrows(BaseBusinessException.class, () -> encryptor.decrypt("invalid"));
    }

    @Test
    void shouldRejectTamperedCiphertext() {
        AesGcmCredentialEncryptor encryptor = new AesGcmCredentialEncryptor("0123456789abcdef0123456789abcdef");
        String encrypted = encryptor.encrypt("secret");
        assertThrows(BaseBusinessException.class, () -> encryptor.decrypt(encrypted + "tampered"));
    }
}
