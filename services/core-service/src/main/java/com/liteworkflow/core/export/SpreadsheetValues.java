package com.liteworkflow.core.export;

final class SpreadsheetValues {

    private static final int XLSX_CELL_LIMIT = 32_767;

    private SpreadsheetValues() {
    }

    static String csvText(Object value) {
        return protectFormula(value == null ? "" : value.toString());
    }

    static String xlsxText(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.length() > XLSX_CELL_LIMIT) {
            text = text.substring(0, XLSX_CELL_LIMIT);
        }
        return text;
    }

    private static String protectFormula(String value) {
        String leadingTrimmed = value.stripLeading();
        if (!leadingTrimmed.isEmpty()) {
            char first = leadingTrimmed.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                return "'" + value;
            }
        }
        return value;
    }
}
