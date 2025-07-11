package bio.coil.CoilEdinburgh;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;


public class OutputResults {

	final String [] resultsToOutput;
	final int cellNum;
	String filenames;
	
	public OutputResults(String[] results, int a, String filename) {
		this.resultsToOutput = results;
        this.cellNum = a;
        this.filenames = filename;
	}
	
	public void run(){
		
	        String CreateName = filenames+ "_Results.csv";
	        
	        try {
	            FileWriter fileWriter = new FileWriter(CreateName, true);
	            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
	            bufferedWriter.newLine();
	            if (cellNum==1) {
	            	bufferedWriter.write("Cell, Pearsons No Thresh, Pearsons Below Thresh, Pearsons Above Thresh, C1 Max Thresh, C2 Max Thresh, Manders tM1, Manders tM2");
	            	bufferedWriter.newLine();
	            }
	            for(int b=0;b<resultsToOutput.length;b++) {
	            	String theValue = SplitText(b);
	            	if (b==0) {
	            		bufferedWriter.write(cellNum + ",");
	            	}
	            	bufferedWriter.write(theValue + ", ");
	            }
	            bufferedWriter.newLine();
	            
	            bufferedWriter.close();
	        } catch (IOException ex) {
	            System.out.println(
	                    "Error writing to file '" + CreateName + "'");
	        }
	    

	}
	
	public String SplitText(int b) {
		int positionIndex = resultsToOutput[b].indexOf(',');
        String afterComma = (positionIndex != -1) ? resultsToOutput[b].substring(positionIndex + 1) : "";
        
        return afterComma;
	}
}
