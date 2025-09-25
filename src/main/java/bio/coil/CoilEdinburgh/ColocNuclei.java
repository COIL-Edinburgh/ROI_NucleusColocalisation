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
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import org.apache.commons.csv.CSVPrinter;

public class ColocNuclei {
    public enum AutoThresholdAlgorithm {
        Costes, Bisection

    }
    public static final String MASK_NAME = "Mask";
    private final Path imageFilePath;
    private final String channel1;
    private final String channel2;
    private final String[] resultsHeader;
    private final AutoThresholdAlgorithm autoThresholdAlgorithm;
    private final Roi[] nucs;
    private final ImagePlus mask;

    public ColocNuclei(Roi[] regions, Path imageFilePath, String channel1, String channel2, AutoThresholdAlgorithm autoThresholdAlgorithm, Roi[] nucOutlines, ImagePlus maskImage) {
    	     
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
        this.nucs = nucOutlines;
        this.mask = maskImage;
    }

    public void run() {
        try {
            RoiManager rm = RoiManager.getRoiManager();
            List<String[]> allCellResults = new ArrayList<>(rm.getCount());
            IJ.selectWindow(channel1);
        	ImagePlus currentWindow = WindowManager.getCurrentImage();
        	
            for (int nucRoiIdx = 0; nucRoiIdx < nucs.length; ++nucRoiIdx) {	
            	
            	int isGreenPositive = 0;
            	isGreenPositive = checkIfItsAGreenCell(currentWindow,nucRoiIdx);
            	int cellRoiIdx = isGreenPositive;
            	if(isGreenPositive>0) {
            	
            		try 	{
            			int maskID = mask.getID();
            			IJ.selectWindow(maskID);
            			IJ.setRawThreshold(mask, cellRoiIdx, cellRoiIdx);
            			IJ.run(mask, "Analyze Particles...", "  show=Masks display clear"); //Make Mask from cellpose regions
                		ImagePlus tempMask = WindowManager.getCurrentImage();
                    	IJ.run(tempMask, "Invert LUT", "");
                    	tempMask.setTitle(MASK_NAME);
                    	IJ.run("Coloc 2", getColoc2options(4, 20));

                    	allCellResults.add(ReadLog(cellRoiIdx));

                    	tempMask.changes = false;
                    	tempMask.close();
                	} catch (Exception e) {
                    	e.printStackTrace();
                	}
            	}
            }
            writeCsvWithHeader(allCellResults);
            IJ.save(mask, imageFilePath + "_Overlay.tif");
            rm.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getColoc2options(int psf, final int costesRandomisations) {
        return String.join(" ", new String[]{
                "channel_1=" + channel1,
                "channel_2=" + channel2,
                "roi_or_mask=[" + MASK_NAME + "]",
                "threshold_regression="+autoThresholdAlgorithm.name(),
                "spearman's_rank_correlation",
                "manders'_correlation",
                "2d_intensity_histogram",
        });
    }

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

    private String[] ReadLog(int cellRoiIdx) {
        String[] theResults = {
                Integer.toString(cellRoiIdx),
                //Get Pearson's R Values
                getTextAfterSearchTermFromLog("Pearson's R value (no threshold),"),
                getTextAfterSearchTermFromLog("Pearson's R value (below threshold),"),
                getTextAfterSearchTermFromLog("Pearson's R value (above threshold),"),
                //Get Spearman's R
                getTextAfterSearchTermFromLog("Spearman's rank correlation value,"),
                //Get channel auto-threshold values and Costes p-value
                getTextAfterSearchTermFromLog("Ch1 Max Threshold,"),
                getTextAfterSearchTermFromLog("Ch2 Max Threshold,"),
                getTextAfterSearchTermFromLog("Costes P-Value,"),
                //Get Manders' M1 and M2 Values
                getTextAfterSearchTermFromLog("Manders' tM1 (Above autothreshold of Ch2),"),
                getTextAfterSearchTermFromLog("Manders' tM2 (Above autothreshold of Ch1),")
        };

        IJ.log("\\Clear");

        //Close pdf window
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

    private int checkIfItsAGreenCell(ImagePlus currentWindow, int cellRoiIdx) {
    	int itsGreen = 0;
    	Roi roiToTest = nucs[cellRoiIdx];
    	mask.setRoi(roiToTest);
    	double regionValue = roiToTest.getStatistics().max;
    	
    	if (regionValue>0){
    		itsGreen = (int) regionValue;
    	}
    	return itsGreen;
    }
}
