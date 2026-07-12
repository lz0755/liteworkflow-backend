package com.liteworkflow.core.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

final class XlsxIssueExportWriter implements IssueExportDocumentWriter {

    private static final List<String> HEADERS = List.of(
            "issueNumber", "issueId", "title", "description", "state", "assigneeIds", "labels",
            "createdBy", "updatedBy", "createdAt", "updatedAt");

    private final Path path;
    private final SXSSFWorkbook workbook;
    private final SXSSFSheet sheet;
    private int rowIndex;

    XlsxIssueExportWriter(Path path) {
        this.path = path;
        this.workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        this.sheet = workbook.createSheet("Issues");
        writeHeader();
    }

    private void writeHeader() {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        Row row = sheet.createRow(rowIndex++);
        for (int index = 0; index < HEADERS.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(HEADERS.get(index));
            cell.setCellStyle(style);
        }
    }

    @Override
    public void write(IssueExportRow value) {
        Row row = sheet.createRow(rowIndex++);
        List<String> cells = List.of(
                Long.toString(value.issueNumber()),
                value.id().toString(),
                value.title(),
                value.description() == null ? "" : value.description(),
                value.state(),
                String.join(";", value.assignees()),
                String.join(";", value.labels()),
                value.createdBy().toString(),
                value.updatedBy().toString(),
                value.createdAt().toString(),
                value.updatedAt().toString());
        for (int index = 0; index < cells.size(); index++) {
            row.createCell(index).setCellValue(SpreadsheetValues.xlsxText(cells.get(index)));
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try (OutputStream output = Files.newOutputStream(path)) {
            workbook.write(output);
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            workbook.close();
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        } finally {
            workbook.dispose();
        }
        if (failure != null) throw failure;
    }
}
