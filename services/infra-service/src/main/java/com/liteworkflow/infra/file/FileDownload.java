package com.liteworkflow.infra.file;

import com.liteworkflow.common.file.storage.ObjectContent;

public record FileDownload(StoredFile file, ObjectContent content) {
}
