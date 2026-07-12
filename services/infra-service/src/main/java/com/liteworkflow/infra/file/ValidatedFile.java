package com.liteworkflow.infra.file;

public record ValidatedFile(byte[] bytes, String originalName, String extension, String contentType, String sha256Hex) {
    public ValidatedFile { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
