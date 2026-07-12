package com.liteworkflow.infra.export;

import com.liteworkflow.common.file.storage.ObjectContent;

public record ExportDownload(ExportFile file, ObjectContent content) {
}
