package com.liteworkflow.infra.file;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.infra.config.FileStorageProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileContentValidator {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "txt", "md", "docx");
    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "md", "docx", "zip");
    private static final Map<String, String> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("zip", "application/zip"));

    private final FileStorageProperties properties;
    public FileContentValidator(FileStorageProperties properties) { this.properties = properties; }

    public ValidatedFile validate(FilePurpose purpose, MultipartFile multipart) {
        if (multipart == null || multipart.isEmpty()) throw new BizException(FileErrorCode.FILE_CONTENT_MISMATCH);
        String name = requireSafeName(multipart.getOriginalFilename());
        String extension = extension(name);
        if (!allowedExtensions(purpose).contains(extension)) {
            throw new BizException(FileErrorCode.FILE_EXTENSION_NOT_ALLOWED);
        }
        long maximum = maximum(purpose);
        if (multipart.getSize() > maximum) throw new BizException(FileErrorCode.FILE_TOO_LARGE);

        byte[] bytes;
        try (var input = multipart.getInputStream()) {
            bytes = input.readNBytes(Math.toIntExact(maximum + 1));
        } catch (IOException exception) {
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED, "Unable to read uploaded file", exception);
        }
        if (bytes.length == 0) throw new BizException(FileErrorCode.FILE_CONTENT_MISMATCH);
        if (bytes.length > maximum) throw new BizException(FileErrorCode.FILE_TOO_LARGE);

        String expected = MIME_BY_EXTENSION.get(extension);
        String declared = normalizeMime(multipart.getContentType());
        String detected = detect(bytes, extension);
        if (!declaredMatches(extension, declared, expected) || !detected.equals(expected)) {
            throw new BizException(FileErrorCode.FILE_CONTENT_MISMATCH);
        }
        return new ValidatedFile(bytes, name, extension, expected, sha256(bytes));
    }

    private String requireSafeName(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.codePoints().anyMatch(value -> Character.isISOControl(value))) {
            throw new BizException(FileErrorCode.FILE_NAME_INVALID);
        }
        return name;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) throw new BizException(FileErrorCode.FILE_EXTENSION_NOT_ALLOWED);
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Set<String> allowedExtensions(FilePurpose purpose) {
        return switch (purpose) {
            case AVATAR, WORKSPACE_ICON, PROJECT_ICON -> IMAGE_EXTENSIONS;
            case PROJECT_DOCUMENT -> DOCUMENT_EXTENSIONS;
            case ATTACHMENT -> ATTACHMENT_EXTENSIONS;
        };
    }

    private long maximum(FilePurpose purpose) {
        return switch (purpose) {
            case AVATAR -> properties.getLimits().getAvatarBytes();
            case WORKSPACE_ICON, PROJECT_ICON -> properties.getLimits().getIconBytes();
            case ATTACHMENT -> properties.getLimits().getAttachmentBytes();
            case PROJECT_DOCUMENT -> properties.getLimits().getDocumentBytes();
        };
    }

    private String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) return "";
        return mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean declaredMatches(String extension, String declared, String expected) {
        if (extension.equals("md")) return declared.equals("text/markdown") || declared.equals("text/plain");
        return declared.equals(expected);
    }

    private String detect(byte[] bytes, String extension) {
        if (starts(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "image/png";
        if (starts(bytes, 0xff, 0xd8, 0xff)) return "image/jpeg";
        if (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")) return "image/gif";
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        if (ascii(bytes, 0, "%PDF-")) return "application/pdf";
        if (starts(bytes, 0x50, 0x4b, 0x03, 0x04)) {
            return isDocx(bytes) ? MIME_BY_EXTENSION.get("docx") : "application/zip";
        }
        if ((extension.equals("txt") || extension.equals("md")) && isUtf8Text(bytes)) {
            return MIME_BY_EXTENSION.get(extension);
        }
        return "application/octet-stream";
    }

    private boolean isDocx(byte[] bytes) {
        boolean contentTypes = false;
        boolean wordDocument = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null && entries++ < 10_000) {
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if ("word/document.xml".equals(name)) wordDocument = true;
                if (contentTypes && wordDocument) return true;
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private boolean starts(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xff) != expected[i]) return false;
        return true;
    }
    private boolean ascii(byte[] bytes, int offset, String expected) {
        byte[] value = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + value.length) return false;
        for (int i = 0; i < value.length; i++) if (bytes[offset + i] != value[i]) return false;
        return true;
    }
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
