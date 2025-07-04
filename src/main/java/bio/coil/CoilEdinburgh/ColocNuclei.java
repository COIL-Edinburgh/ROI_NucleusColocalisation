package bio.coil.CoilEdinburgh;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.Window.Type;

import org.scijava.Context;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.text.TextWindow;

public class ColocNuclei {
//	Roi[] outlines;
	ImagePlus regions;
	ImagePlus[] channels;
	
	public ColocNuclei(ImagePlus regions, ImagePlus[] channels) {
		//IJ.setAutoThreshold(channels[3], "Default dark");
		int regionsID = regions.getID();
		int thresholdValue = 1;
		ResultsTable rt = new ResultsTable();
		boolean allCounted = true;
		do {
			IJ.selectWindow(regionsID);
			IJ.setRawThreshold(regions, 1, thresholdValue);
			IJ.run(regions, "Analyze Particles...", "  show=Masks display clear"); //Make Mask from cellpose regions
			rt=Analyzer.getResultsTable();
			if(rt.getCounter()>0) {
				allCounted = false;
			}
			
		//	channels[3].setRoi(outlines[a]);
		//	if (channels[3].isLockedByAnotherThread()==true) {
		//		channels[3].unlock();
		//	}
		//	channels[3].unlock();
			
			ImagePlus tempMask = WindowManager.getCurrentImage();
			IJ.run(tempMask, "Invert LUT", "");
			tempMask.setTitle("Mask");
		
			IJ.run("Coloc 2", "channel_1=RED channel_2=GREEN roi_or_mask=[Mask] threshold_regression=Costes spearman's_rank_correlation manders'_correlation 2d_intensity_histogram costes'_significance_test psf=4 costes_randomisations=20");
		
		
	//	IJ.run("Coloc 2", "channel_1=DUP_DNMT3C-HAHA_E16_2_rbHA_1_in_200-a568_rtHP1b_1_in_500-a647_4.czi channel_2=DUP_DNMT3C-HAHA_E16_2_rbHA_1_in_200-a568_rtHP1b_1_in_500-a647_4.czi roi_or_mask=[Mask of DUP_DNMT3C-HAHA_E16_2_rbHA_1_in_200-a568_rtHP1b_1_in_500-a647_4.czi] threshold_regression=Costes spearman's_rank_correlation manders'_correlation 2d_intensity_histogram costes'_significance_test psf=4 costes_randomisations=20");
			ReadLog();
			
			tempMask.changes=false;
			tempMask.close();
			
			thresholdValue++;
		}while(allCounted==false );
	}
	
	private void ReadLog() {
		
		//Get Pearsons Values
		String searchTerm = "Pearson's R value (no threshold), ";
		String pearsonsNoThresh = GetTextFromLog(searchTerm);
		searchTerm = "Pearson's R value (below threshold), ";
		String pearsonsBelowThresh = GetTextFromLog(searchTerm);
		searchTerm = "Pearson's R value (above threshold), ";
		String pearsonsAboveThresh = GetTextFromLog(searchTerm);
		//Get Costes autothreshold values
		searchTerm = "Ch1 Max Threshold, ";
		String chOneMaxThresh = GetTextFromLog(searchTerm);
		searchTerm = "Ch2 Max Threshold, ";
		String chTwoMaxThresh = GetTextFromLog(searchTerm);
		//Get Manders Values
		searchTerm = "Manders' tM1 (Above autothreshold of Ch2), ";
		String mandersMOne = GetTextFromLog(searchTerm);
		searchTerm = "Manders' tM2 (Above autothreshold of Ch1), ";
		String mandersMTwo = GetTextFromLog(searchTerm);
		
		IJ.log("\\Clear");
		
		//Close pdf window
		
		 Window[] windows = WindowManager.getAllNonImageWindows();
	        for (Window win : windows) {
	        	String title = "";
	            if (win instanceof Frame) {
	                title = ((Frame) win).getTitle();
	            } else if (win instanceof Dialog) {
	                title = ((Dialog) win).getTitle();
	                win.dispose();;
	            }
	            if(title.contains("Colocalization_of_")) {
	            	win.dispose();
	            }
	        }
		
	}
	
	private String GetTextFromLog(String searchTerm) {
		String textContent = null;
		String logContents = IJ.getLog();
		
		int startIndexPosition = logContents.indexOf(searchTerm);
		//String searchTermreturn = indexPosition != -1 ? logContents.substring(indexPosition + searchTerm.length()) : "";
		
		int lineBreakIndex = logContents.indexOf('\n', startIndexPosition);
		
		textContent = logContents.substring(startIndexPosition, lineBreakIndex);
		return textContent;
	}
	
}
