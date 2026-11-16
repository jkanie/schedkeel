package io.github.jkanie.schedkeel.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A plain-text table with columns padded to their widest cell, or the same rows as CSV. */
final class Table {

    private final List<String> headers;
    private final List<List<String>> rows = new ArrayList<>();

    Table(String... headers) {
        this.headers = List.of(headers);
    }

    Table row(Object... cells) {
        if (cells.length != headers.size()) {
            throw new IllegalArgumentException("Expected " + headers.size() + " cells, got " + cells.length);
        }
        rows.add(Arrays.stream(cells).map(c -> c == null ? null : c.toString()).toList());
        return this;
    }

    String render(String empty) {
        if (rows.isEmpty()) {
            return empty;
        }
        List<List<String>> shown = rows.stream()
                .map(row -> row.stream().map(c -> c == null ? "-" : c).toList())
                .toList();
        int[] widths = new int[headers.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = headers.get(i).length();
            for (List<String> row : shown) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        StringBuilder out = new StringBuilder();
        line(out, headers, widths);
        for (List<String> row : shown) {
            line(out, row, widths);
        }
        return out.toString().stripTrailing();
    }

    /**
     * The header and the rows as CSV, for a spreadsheet or a script: a comma between cells, and a cell that holds a
     * comma, a quote, a line break in quotes, with each quote doubled, as RFC 4180 has it. A missing value is an
     * empty cell. With no rows, only the header line is printed, such that a script still sees the columns.
     */
    String csv() {
        StringBuilder out = new StringBuilder();
        csvLine(out, headers);
        for (List<String> row : rows) {
            csvLine(out, row);
        }
        return out.substring(0, out.length() - 1);
    }

    private static void csvLine(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i) == null ? "" : cells.get(i);
            if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
                cell = '"' + cell.replace("\"", "\"\"") + '"';
            }
            out.append(i == 0 ? "" : ",").append(cell);
        }
        out.append('\n');
    }

    private static void line(StringBuilder out, List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            line.append(String.format("%-" + widths[i] + "s", cells.get(i)));
            if (i < cells.size() - 1) {
                line.append("  ");
            }
        }
        out.append(line.toString().stripTrailing()).append('\n');
    }
}
