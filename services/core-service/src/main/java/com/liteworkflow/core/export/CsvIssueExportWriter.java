package com.liteworkflow.core.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class CsvIssueExportWriter implements IssueExportDocumentWriter {

    private static final List<String> HEADERS = List.of(
            "issueNumber", "issueId", "title", "description", "state", "assigneeIds", "labels",
            "createdBy", "updatedBy", "createdAt", "updatedAt");

    private final BufferedWriter output;

    CsvIssueExportWriter(Path path) throws IOException {
        output = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        writeValues(HEADERS);
    }

    @Override
    public void write(IssueExportRow row) throws IOException {
        writeValues(List.of(
                Long.toString(row.issueNumber()),
                row.id().toString(),
                row.title(),
                row.description() == null ? "" : row.description(),
                row.state(),
                String.join(";", row.assignees()),
                String.join(";", row.labels()),
                row.createdBy().toString(),
                row.updatedBy().toString(),
                row.createdAt().toString(),
                row.updatedAt().toString()));
    }

    private void writeValues(List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) output.write(',');
            writeEscaped(SpreadsheetValues.csvText(values.get(index)));
        }
        output.write("\r\n");
    }

    private void writeEscaped(String value) throws IOException {
        boolean quoted = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
        if (!quoted) {
            output.write(value);
            return;
        }
        output.write('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') output.write('"');
            output.write(character);
        }
        output.write('"');
    }

    @Override
    public void close() throws IOException {
        output.close();
    }
}
