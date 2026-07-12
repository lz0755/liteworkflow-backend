package com.liteworkflow.infra.file;

public enum FilePurpose {
    ATTACHMENT("attachments", FileScope.ISSUE),
    AVATAR("avatars", FileScope.USER),
    WORKSPACE_ICON("icons/workspaces", FileScope.WORKSPACE),
    PROJECT_ICON("icons/projects", FileScope.PROJECT),
    PROJECT_DOCUMENT("rag-documents", FileScope.PROJECT);

    private final String keyPrefix;
    private final FileScope scope;
    FilePurpose(String keyPrefix, FileScope scope) { this.keyPrefix = keyPrefix; this.scope = scope; }
    public String keyPrefix() { return keyPrefix; }
    public FileScope scope() { return scope; }
}
