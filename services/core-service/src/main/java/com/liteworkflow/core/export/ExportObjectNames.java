package com.liteworkflow.core.export;

import com.liteworkflow.common.file.storage.ObjectKeys;
import java.util.UUID;

final class ExportObjectNames {
    private ExportObjectNames() {
    }

    static String objectKey(UUID workspaceId, UUID projectId, UUID jobId, IssueExportFormat format) {
        return ObjectKeys.requireSafe("exports/" + workspaceId + "/" + projectId + "/" + jobId
                + "." + format.extension());
    }

    static String fileName(UUID projectId, UUID jobId, IssueExportFormat format) {
        return "issues-" + projectId + "-" + jobId + "." + format.extension();
    }
}
