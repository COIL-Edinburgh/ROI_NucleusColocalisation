package bio.coil.CoilEdinburgh;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.RoiManager;
import org.apache.commons.csv.CSVPrinter;

@SuppressWarnings("CallToPrintStackTrace")
public class ColocNuclei {
    public enum AutoThresholdAlgorithm {
        Costes, Bisection

    }
    public static final String MASK_NAME = "Mask";
    private final ImagePlus regionsImp;
    private final Path imageFilePath;
    private final String channel1;
    private final String channel2;
    private final String[] resultsHeader;
    private final AutoThresholdAlgorithm autoThresholdAlgorithm;

    public ColocNuclei(ImagePlus regions, Path imageFilePath, String channel1, String channel2, AutoThresholdAlgorithm autoThresholdAlgorithm) {
        this.regionsImp = regions;
        this.imageFilePath = imageFilePath;
        this.channel1 = channel1;
        this.channel2 = channel2;
        resultsHeader = new String[]{
                "Cell",
                "Pearson's R (No threshold)",
                "Pearson's R (Below threshold)",
                "Pearson's R (Above threshold)",
                "Spearman's R",
                String.format("%s channel (C1) Costes auto threshold", channel1),
                String.format("%s channel (C2) Costes auto threshold", channel2),
                "Costes p-value",
                String.format("Manders' tM1 (above %s channel (C2) threshold)", channel2),
                String.format("Manders' tM2 (above %s channel (C1) threshold)", channel1)
        };
        this.autoThresholdAlgorithm = autoThresholdAlgorithm;
    }

    public void run() {
        try {
            // Get image window ID
            int regionsID = regionsImp.getID();

            RoiManager rm = RoiManager.getRoiManager();
            List<String[]> allCellResults = new ArrayList<>(rm.getCount());
            for (int cellRoiIdx = 1; cellRoiIdx < rm.getCount() + 1; ++cellRoiIdx) {
                try {
                    // Select image window
                    IJ.selectWindow(regionsID);
                    // Set threshold for masking out everything except the current Cellpose RoI
                    IJ.setRawThreshold(regionsImp, cellRoiIdx, cellRoiIdx);
                    // Make mask for Coloc 2 from the current Cellpose RoI
                    IJ.run(regionsImp, "Analyze Particles...", "  show=Masks display clear");

                    ImagePlus tempMask = WindowManager.getCurrentImage();
                    IJ.run(tempMask, "Invert LUT", "");
                    tempMask.setTitle(MASK_NAME);
                    // Run Coloc 2
                    IJ.run("Coloc 2", getColoc2options(4, 20));
                    // Store Coloc 2 results
                    allCellResults.add(ReadLog(cellRoiIdx));
                    // Reset temporary mask image
                    tempMask.changes = false;
                    tempMask.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Write Coloc 2 results and Cellpose masks if needed for manual review
            writeCsvWithHeader(allCellResults);
            IJ.save(regionsImp, imageFilePath + "_Overlay.tif");
            // Clean up RoIs to prepare for next image
            rm.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Format options for running Coloc 2
     * @param psf 4
     * @param costesRandomisations 20
     * @return string of options for running Coloc 2
     */
    @SuppressWarnings({"SameParameterValue"})
    private String getColoc2options(int psf, final int costesRandomisations) {
        return String.join(" ", new String[]{
                "channel_1=" + channel1,
                "channel_2=" + channel2,
                "roi_or_mask=[" + MASK_NAME + "]",
                "threshold_regression="+autoThresholdAlgorithm.name(),
                "spearman's_rank_correlation",
                "manders'_correlation",
                "2d_intensity_histogram",
                "costes'_significance_test",
                "psf=" + psf,
                "costes_randomisations=" + costesRandomisations,
        });
    }

    /**
     * Write tab-separated values file to `resultsFilePath` with header `resultsHeader` and the provided entries
     * @param rows list of string arrays where each string is a field and each array is a row/entry
     */
    private void writeCsvWithHeader(List<String[]> rows) {
        String resultsFileName = imageFilePath.getFileName().toString() + String.format("_results_%s_%s.tsv", channel1, channel2);
        Path resultsFilePath = Paths.get(imageFilePath.getParent().toString(), resultsFileName);

        try (FileWriter fileWriter = new FileWriter(resultsFilePath.toFile(), false);
             CSVPrinter printer = Nucleus_Coloc.getTsvBaseFormat()
                     .setHeader(resultsHeader).get()
                     .print(fileWriter)) {
            printer.printRecords(rows);
        } catch (IOException ex) {
            System.out.println("Error writing to file '" + resultsFileName + "'");
        }
    }

    /**
     * Read the ImageJ log to parse the output from running the Coloc 2 plugin
     * @param cellRoiIdx index of the cell/RoI being analysed
     * @return Coloc 2 output ready to be written to output TSV file
     */
    private String[] ReadLog(int cellRoiIdx) {
        String[] theResults = {
                Integer.toString(cellRoiIdx),
                // Get Pearson's R Values
                getTextAfterSearchTermFromLog("Pearson's R value (no threshold),"),
                getTextAfterSearchTermFromLog("Pearson's R value (below threshold),"),
                getTextAfterSearchTermFromLog("Pearson's R value (above threshold),"),
                // Get Spearman's R Value
                getTextAfterSearchTermFromLog("Spearman's rank correlation value,"),
                // Get channel auto-threshold values and Costes p-value
                getTextAfterSearchTermFromLog("Ch1 Max Threshold,"),
                getTextAfterSearchTermFromLog("Ch2 Max Threshold,"),
                getTextAfterSearchTermFromLog("Costes P-Value,"),
                // Get Manders' M1 and M2 Values
                getTextAfterSearchTermFromLog("Manders' tM1 (Above autothreshold of Ch2),"),
                getTextAfterSearchTermFromLog("Manders' tM2 (Above autothreshold of Ch1),")
        };

        // Clear the ImageJ log to ensure we get the latest and not the first search results
        IJ.log("\\Clear");

        // Close dialogs and pdf windows
        Window[] windows = WindowManager.getAllNonImageWindows();
        for (Window win : windows) {
            String title = "";
            if (win instanceof Frame) {
                title = ((Frame) win).getTitle();
            } else if (win instanceof Dialog) {
                title = ((Dialog) win).getTitle();
                win.dispose();
            }
            if (title.contains("Colocalization_of_")) {
                win.dispose();
            }
        }
        return theResults;
    }

    /**
     * Searches the ImageJ log window for the provided search term and returns the text following it
     * @param searchTerm text to search to ImageJ log for
     * @return text following the search term
     */
    private String getTextAfterSearchTermFromLog(String searchTerm) {
        String logContents = IJ.getLog();
        int startIndexPosition = logContents.indexOf(searchTerm);
        if (startIndexPosition < 0) {
            return "";
        }
        int lineBreakIndex = logContents.indexOf('\n', startIndexPosition);
        // Strip actual search term from result
        return logContents.substring(startIndexPosition + searchTerm.length() + 1, lineBreakIndex).trim();
    }

}
