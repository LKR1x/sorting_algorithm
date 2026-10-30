// Import the tools used to build the window and work with data.
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;


public class App extends JFrame {

    // Create the buttons and the column selection list.
    private final JButton openBtn = new JButton("Open CSV");
    private final JButton runBtn = new JButton("Compare All Five");
    private final JButton cancelBtn = new JButton("Cancel");
    private final JComboBox<String> columnsDropdown = new JComboBox<>();

    // These labels show file details, progress messages, and the fastest algorithm.
    private final JLabel fileInfoLabel = new JLabel("No dataset loaded");
    private final JLabel statusLabel = new JLabel("Open a CSV file to begin.");
    private final JLabel winnerLabel = new JLabel("Your fastest algorithm will appear here.");

    // Store the data displayed in the preview and results tables.
    private final DefaultTableModel previewModel = createNonEditableModel();
    private final DefaultTableModel resultsModel = createNonEditableModel("Algorithm", "Time (ms)", "Verification");

    // Create the tables, sorted text area, and progress bar.
    private final JTable previewTable = new JTable(previewModel);
    private final JTable resultsTable = new JTable(resultsModel);
    private final JTextArea sortedArea = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar();

    // Keep the loaded dataset and the background comparison task.
    private DatasetManager datasetManager;
    private SwingWorker<Void, String> benchmarkWorker;

    // Build the app window and set up its controls.
    public App() {
        super("Sorting Lab | Algorithm Performance Evaluation");
        // Close the app when the window closes, and set its size and position.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(850, 650));
        setSize(1100, 820);
        setLocationRelativeTo(null);

        // Main Layout
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));
        setContentPane(root);

        // --- TOP SECTION: Title & Controls ---
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Sorting Algorithm Evaluator");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        top.add(title);
        top.add(new JLabel("Upload a CSV file, select a numeric column, and compare sorting algorithm performance."));
        top.add(Box.createVerticalStrut(12));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controls.add(openBtn);
        controls.add(new JLabel("Numeric Column:"));
        columnsDropdown.setPreferredSize(new Dimension(220, 28));
        controls.add(columnsDropdown);
        controls.add(runBtn);
        controls.add(cancelBtn);
        top.add(controls);
        top.add(Box.createVerticalStrut(8));
        top.add(fileInfoLabel);

        // Align the items in the top section to the left.
        for (Component c : top.getComponents()) {
            if (c instanceof JComponent) ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        root.add(top, BorderLayout.NORTH);

        // --- CENTER SECTION: Dataset Preview & Performance Split Panes ---
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        previewTable.setRowHeight(24);
        resultsTable.setRowHeight(28);

        JPanel dataPreviewPanel = createSectionPanel("Dataset Preview (First 200 Rows)", new JScrollPane(previewTable));

        JPanel bottomResultsPanel = new JPanel(new BorderLayout(10, 10));
        winnerLabel.setFont(winnerLabel.getFont().deriveFont(Font.BOLD, 16f));
        winnerLabel.setForeground(new Color(0, 120, 215));
        bottomResultsPanel.add(winnerLabel, BorderLayout.NORTH);

        // Let users read the sorted values without editing them.
        sortedArea.setEditable(false);
        sortedArea.setLineWrap(true);
        sortedArea.setWrapStyleWord(true);
        sortedArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sortedArea.setMargin(new Insets(8, 8, 8, 8));

        // Place the performance table and sorted values side by side.
        JSplitPane comparisonSplit = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            createSectionPanel("Performance - Execution Time (ms)", new JScrollPane(resultsTable)),
            createSectionPanel("Sorted Output (First 200 Ascending)", new JScrollPane(sortedArea))
        );
        comparisonSplit.setResizeWeight(0.5);
        bottomResultsPanel.add(comparisonSplit, BorderLayout.CENTER);

        // Place the dataset preview above the comparison results.
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, dataPreviewPanel, bottomResultsPanel);
        mainSplit.setResizeWeight(0.42);
        root.add(mainSplit, BorderLayout.CENTER);

        // --- FOOTER SECTION: Status & Progress ---
        JPanel footer = new JPanel(new BorderLayout(10, 8));
        footer.add(statusLabel, BorderLayout.WEST);
        progressBar.setPreferredSize(new Dimension(180, 18));
        footer.add(progressBar, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        // Initial Button States
        runBtn.setEnabled(false);
        columnsDropdown.setEnabled(false);
        cancelBtn.setEnabled(false);

        // Event Listeners
        openBtn.addActionListener(e -> chooseFile());
        runBtn.addActionListener(e -> runBenchmark());
        cancelBtn.addActionListener(e -> {
            if (benchmarkWorker != null) {
                // Request cancellation. The worker checks for it between algorithms.
                benchmarkWorker.cancel(true);
                statusLabel.setText("Benchmark cancelled.");
            }
        });
    }

    // Create a table model that does not allow users to edit cells.
    private static DefaultTableModel createNonEditableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }

    // Create a section with a bold heading above its content.
    private static JPanel createSectionPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    // Update the controls and progress animation while a task is running.
    private void setBusy(boolean busy) {
        openBtn.setEnabled(!busy);
        columnsDropdown.setEnabled(!busy && datasetManager != null);
        runBtn.setEnabled(!busy && datasetManager != null);
        cancelBtn.setEnabled(busy);
        progressBar.setIndeterminate(busy);
    }

    // Remove the previous results before loading or comparing new data.
    private void clearResults() {
        resultsModel.setRowCount(0);
        sortedArea.setText("");
        winnerLabel.setText("Your fastest algorithm will appear here.");
    }

    // Step 1: File Loading (Member 1 Integration)
    private void chooseFile() {
        // Open a file picker with a CSV file filter.
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV datasets (*.csv)", "csv"));
        // Stop here if the user closes or cancels the file picker.
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File selectedFile = chooser.getSelectedFile();
        setBusy(true);
        statusLabel.setText("Loading CSV file: " + selectedFile.getName() + "...");

        // Load the file in the background so the window can still respond.
        SwingWorker<DatasetManager, Void> loaderWorker = new SwingWorker<>() {
            @Override
            protected DatasetManager doInBackground() throws Exception {
                // Ask DatasetManager to read the selected CSV file.
                return DatasetManager.load(selectedFile);
            }

            @Override
            protected void done() {
                try {
                    // Get the loaded dataset after the background task finishes.
                    datasetManager = get();
                    // Replace the column list with the headers from the new file.
                    columnsDropdown.removeAllItems();
                    for (int i = 0; i < datasetManager.getHeaders().length; i++) {
                        columnsDropdown.addItem((i + 1) + ". " + datasetManager.getHeaders()[i]);
                    }

                    // Populate Preview JTable
                    previewModel.setDataVector(datasetManager.getPreviewData(200), datasetManager.getHeaders());
                    for (int i = 0; i < previewTable.getColumnCount(); i++) {
                        previewTable.getColumnModel().getColumn(i).setPreferredWidth(140);
                    }

                    // Show the file name and the number of rows and columns.
                    fileInfoLabel.setText(selectedFile.getName() + "  |  " 
                        + datasetManager.getRows().size() + " rows  |  " 
                        + datasetManager.getHeaders().length + " columns");

                    clearResults();
                    statusLabel.setText("CSV loaded. Select a numeric column and click 'Compare All Five'.");
                } catch (Exception ex) {
                    // Find the error message to show to the user.
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Error loading file: " + cause.getMessage());
                    JOptionPane.showMessageDialog(App.this, cause.getMessage(), "File Load Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false);
                }
            }
        };
        // Start the background file loading task.
        loaderWorker.execute();
    }

    // Step 2: Benchmark Execution & Evaluation (Member 3 Integration)
    private void runBenchmark() {
        // Find the selected column and check that a dataset is available.
        int selectedCol = columnsDropdown.getSelectedIndex();
        if (datasetManager == null || selectedCol < 0) return;

        clearResults();
        setBusy(true);
        statusLabel.setText("Validating numeric data...");

        // Run the sorting comparison in the background.
        benchmarkWorker = new SwingWorker<>() {
            // Keep the original numbers and a correctly sorted copy for checking results.
            double[] originalData;
            double[] expectedSorted;

            @Override
            protected Void doInBackground() throws Exception {
                // Member 1 call
                // Read the selected column as numbers.
                originalData = datasetManager.getNumericColumn(selectedCol);

                // Create expected array for verification
                expectedSorted = originalData.clone();
                Arrays.sort(expectedSorted);

                // ---> FIX: Generate and publish the sorted output text exactly ONCE here <---
                StringBuilder sb = new StringBuilder();
                // Show up to 200 values to keep the output easy to read.
                int limit = Math.min(200, expectedSorted.length);
                for (int k = 0; k < limit; k++) {
                    if (k > 0) sb.append(", ");
                    sb.append(expectedSorted[k]);
                }
                if (expectedSorted.length > 200) {
                    sb.append("\n\n[Showing first 200 of ").append(expectedSorted.length).append(" sorted items]");
                }
                // Send the sorted preview to the window.
                publish("OUTPUT:" + sb.toString());

                // Track the fastest algorithm that produces a correct result.
                double bestTime = Double.MAX_VALUE;
                String bestAlgo = "";

                // Evaluate each of the 5 algorithms
                for (int i = 0; i < SortingAlgorithms.NAMES.length; i++) {
                    // Stop before the next algorithm if cancellation was requested.
                    if (isCancelled()) break;

                    String name = SortingAlgorithms.NAMES[i];
                    publish("Running " + name + " on " + originalData.length + " items...");

                    // Give each algorithm the same starting data using a fresh copy.
                    double[] copy = originalData.clone();
                    // Start timing after the data has been copied.
                    long start = System.nanoTime();

                    // Run the sorting algorithm for the current index.
                    switch (i) {
                        case 0 -> SortingAlgorithms.insertionSort(copy);
                        case 1 -> SortingAlgorithms.shellSort(copy);
                        case 2 -> SortingAlgorithms.mergeSort(copy, 0, copy.length - 1);
                        case 3 -> SortingAlgorithms.quickSort(copy, 0, copy.length - 1);
                        case 4 -> SortingAlgorithms.heapSort(copy);
                    }

                    // Measure the sorting time and convert it to milliseconds.
                    long durationNanos = System.nanoTime() - start;
                    double durationMs = durationNanos / 1_000_000.0;

                    // Check the result against the copy sorted by Java.
                    boolean isCorrect = Arrays.equals(copy, expectedSorted);
                    String verifyText = isCorrect ? "Passed" : "FAILED";

                    // Send the algorithm name, time, and check result to the table.
                    publish("RESULT:" + name + ":" + String.format("%.4f", durationMs) + ":" + verifyText);

                    // Update the winner only when the result is correct and faster.
                    if (isCorrect && durationMs < bestTime) {
                        bestTime = durationMs;
                        bestAlgo = name;
                    }
                }

                // Announce the winner if the task finished without cancellation.
                if (!isCancelled() && !bestAlgo.isEmpty()) {
                    publish("WINNER:" + bestAlgo + ":" + String.format("%.4f", bestTime));
                }
                return null;
            }

            @Override
            // Update the window safely using messages from the background task.
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    // Use the message prefix to decide which part of the window to update.
                    if (msg.startsWith("RESULT:")) {
                        String[] parts = msg.split(":");
                        resultsModel.addRow(new Object[]{parts[1], parts[2], parts[3]});
                    } else if (msg.startsWith("OUTPUT:")) {
                        sortedArea.setText(msg.substring(7));
                        // Show the beginning of the sorted output.
                        sortedArea.setCaretPosition(0);
                    } else if (msg.startsWith("WINNER:")) {
                        String[] parts = msg.split(":");
                        winnerLabel.setText("★ FASTEST ALGORITHM: " + parts[1] + " (" + parts[2] + " ms) ★");
                        // Highlight winning row in table
                        for (int r = 0; r < resultsModel.getRowCount(); r++) {
                            if (resultsModel.getValueAt(r, 0).equals(parts[1])) {
                                resultsTable.setRowSelectionInterval(r, r);
                                break;
                            }
                        }
                    } else {
                        statusLabel.setText(msg);
                    }
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    if (!isCancelled()) {
                        // Check whether the background comparison ended with an error.
                        get();
                        statusLabel.setText("Evaluation completed successfully.");
                    }
                } catch (Exception ex) {
                    // Find the error message to show to the user.
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Error: " + cause.getMessage());
                    JOptionPane.showMessageDialog(App.this, cause.getMessage(), "Evaluation Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        // Start the background sorting comparison.
        benchmarkWorker.execute();
    }

    // Start the application.
    public static void main(String[] args) {
        // Create and display the window on the Swing user interface thread.
        SwingUtilities.invokeLater(() -> {
            try {
                // Use the appearance of the operating system for the controls.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            // Create the app window and show it on screen.
            new App().setVisible(true);
        });
    }
}