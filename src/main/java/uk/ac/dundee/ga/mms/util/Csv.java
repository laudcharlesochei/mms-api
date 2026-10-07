package uk.ac.dundee.ga.mms.util;

import java.util.List;

/** Minimal RFC 4180 CSV writer, with spreadsheet formula-injection protection. */
public final class Csv {

    private Csv() {
    }

    public static String row(List<?> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    public static String cell(Object o) {
        if (o == null) {
            return "";
        }
        String s = o.toString();
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(o instanceof Number)) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
