package com.liteworkflow.infra.file;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.infra.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileContentValidatorTest {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private final FileStorageProperties properties = new FileStorageProperties();
    private final FileContentValidator validator = new FileContentValidator(properties);

    @Test
    void rejectsPathTraversalFileNameEvenThoughObjectKeysAreServerGenerated() {
        var file = new MockMultipartFile("file", "../../avatar.png", "image/png", PNG);
        assertError(() -> validator.validate(FilePurpose.AVATAR, file), FileErrorCode.FILE_NAME_INVALID);
    }

    @Test
    void rejectsFileOverPurposeLimit() {
        properties.getLimits().setAvatarBytes(4);
        var file = new MockMultipartFile("file", "avatar.png", "image/png", PNG);
        assertError(() -> validator.validate(FilePurpose.AVATAR, file), FileErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void rejectsDeclaredMimeAndSignatureMismatch() {
        var file = new MockMultipartFile("file", "payload.png", "image/png", "not an image".getBytes());
        assertError(() -> validator.validate(FilePurpose.AVATAR, file), FileErrorCode.FILE_CONTENT_MISMATCH);
    }

    private void assertError(Runnable action, FileErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
