package com.liteworkflow.core.export;

import java.io.IOException;

interface IssueExportDocumentWriter extends AutoCloseable {
    void write(IssueExportRow row) throws IOException;
    @Override void close() throws IOException;
}
