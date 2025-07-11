package bio.coil.CoilEdinburgh;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.RoiManager;

public class ColocNuclei {
	ImagePlus regionsImp;
	String filenames;
	
	public ColocNuclei(ImagePlus regions, String filename) {
		this.regionsImp = regions;
		this.filenames = filename;
	}	
		
	public void run(){
		int regionsID = regionsImp.getID();
	
		RoiManager rm = RoiManager.getRoiManager();
		
		for (int a=1;a<rm.getCount()+1;a++) {

			IJ.selectWindow(regionsID);
			IJ.setRawThreshold(regionsImp, a, a);
			IJ.run(regionsImp, "Analyze Particles...", "  show=Masks display clear"); //Make Mask from cellpose regions
		
			ImagePlus tempMask = WindowManager.getCurrentImage();
			IJ.run(tempMask, "Invert LUT", "");
			tempMask.setTitle("Mask");
		
			IJ.run("Coloc 2", "channel_1=RED channel_2=GREEN roi_or_mask=[Mask] threshold_regression=Costes spearman's_rank_correlation manders'_correlation 2d_intensity_histogram costes'_significance_test psf=4 costes_randomisations=20");
		
			String results[] = ReadLog();
			
			OutputResults output = new OutputResults(results,a,filenames);
			output.run();
			
			tempMask.changes=false;
			tempMask.close();
			
			
		}
		IJ.save(regionsImp, filenames + "_Overlay.tif");
		rm.reset();
	}
	
	private String[] ReadLog() {
		
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
		
		String [] theResults = new String [7];
		theResults[0]=pearsonsNoThresh;
		theResults[1]=pearsonsBelowThresh;
		theResults[2]=pearsonsAboveThresh;
		theResults[3]=chOneMaxThresh;
		theResults[4]=chTwoMaxThresh;
		theResults[5]=mandersMOne;
		theResults[6]=mandersMTwo;
		
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
		return theResults;
	}
	
	private String GetTextFromLog(String searchTerm) {
		String textContent = null;
		String logContents = IJ.getLog();
		
		int startIndexPosition = logContents.indexOf(searchTerm);
		int lineBreakIndex = logContents.indexOf('\n', startIndexPosition);
		textContent = logContents.substring(startIndexPosition, lineBreakIndex);
		return textContent;
	}
	
}
