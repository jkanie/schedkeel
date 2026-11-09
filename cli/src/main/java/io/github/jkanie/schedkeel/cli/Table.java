package io.github.jkanie.schedkeel.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A plain-text table with columns padded to their widest cell. */
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
        rows.add(Arrays.stream(cells).map(c -> c == null ? "-" : c.toString()).toList());
        return this;
    }

    String render(String empty) {
        if (rows.isEmpty()) {
            return empty;
        }
        int[] widths = new int[headers.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = headers.get(i).length();
            for (List<String> row : rows) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        StringBuilder out = new StringBuilder();
        line(out, headers, widths);
        for (List<String> row : rows) {
            line(out, row, widths);
        }
        return out.toString().stripTrailing();
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
