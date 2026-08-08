package com.dbfleetops.database.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsWithANewNonceAndDecrypts() {
        CredentialCipher cipher = new CredentialCipher(KEY);

        String first = cipher.encrypt("secret");
        String second = cipher.encrypt("secret");

        assertThat(first).startsWith("v1:").isNotEqualTo(second).doesNotContain("secret");
        assertThat(cipher.decrypt(first)).isEqualTo("secret");
        assertThat(cipher.decrypt(second)).isEqualTo("secret");
    }

    @Test
    void rejectsTamperedCiphertextAndWrongSizedKey() {
        CredentialCipher cipher = new CredentialCipher(KEY);
        String encrypted = cipher.encrypt("secret");

        assertThatThrownBy(() -> cipher.decrypt(encrypted + "broken"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new CredentialCipher("YQ=="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
