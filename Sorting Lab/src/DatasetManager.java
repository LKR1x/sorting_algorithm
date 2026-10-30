import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MEMBER 1 RESPONSIBILITY: Dataset Handling & CSV Parsing
 * Loads CSV files, extracts headers, generates dataset preview data,
 * and parses numeric column values for sorting.
 */
public class DatasetManager {

    private final String[] headers;
    private final List<String[]> rows;

    public DatasetManager(String[] headers, List<String[]> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public String[] getHeaders() {
        return headers;
    }

    public List<String[]> getRows() {
        return rows;
    }

    // Loads and parses a CSV file
    public static DatasetManager load(File file) throws IOException {
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            throw new IOException("Please select a valid .csv file.");
        }

        List<String[]> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                // Remove UTF-8 BOM if present
                if (firstLine && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                firstLine = false;

                if (line.trim().isEmpty()) continue;

                // Simple comma split handling basic quotes
                String[] cells = line.split(",", -1);
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = cells[i].trim().replaceAll("^\"|\"$", ""); // Remove wrapping quotes
                }
                records.add(cells);
            }
        }

        if (records.isEmpty()) {
            throw new IOException("The selected CSV file is empty.");
        }

        String[] headers = records.remove(0);
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].isEmpty()) {
                headers[i] = "Column " + (i + 1);
            }
        }

        if (records.isEmpty()) {
            throw new IOException("CSV contains headers but no data rows.");
        }

        return new DatasetManager(headers, records);
    }

    // Returns preview data (up to 200 rows) for the GUI JTable
    public Object[][] getPreviewData(int maxRows) {
        int count = Math.min(maxRows, rows.size());
        Object[][] data = new Object[count][headers.length];
        for (int i = 0; i < count; i++) {
            data[i] = rows.get(i);
        }
        return data;
    }

    // Extracts numeric values from the specified column index
    public double[] getNumericColumn(int columnIndex) throws IllegalArgumentException {
        if (columnIndex < 0 || columnIndex >= headers.length) {
            throw new IllegalArgumentException("Invalid column selection.");
        }

        double[] values = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (columnIndex >= row.length) {
                throw new IllegalArgumentException("Row " + (i + 1) + " is missing column " + headers[columnIndex]);
            }

            String valStr = row[columnIndex].trim();
            try {
                values[i] = Double.parseDouble(valStr);
                if (!Double.isFinite(values[i])) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Row " + (i + 1) + " in column '" + headers[columnIndex] 
                    + "' contains non-numeric value: \"" + valStr + "\". Please select a numeric column.");
            }
        }
        return values;
    }
}
