/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package bio.coil.CoilEdinburgh;

/*
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.WaitForUserDialog;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;

import io.scif.*;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.roi.ROIService;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import java.io.IOException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;


/**
 * This example illustrates how to create an ImageJ {@link Command} plugin.
 * <p>
 * </p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>Users Plugins>Nucleus Colocalisation")
public class Nucleus_Coloc<T extends RealType<T>> implements Command {
 
    @Parameter
    private FormatService formatService;

    @Parameter
    private DatasetIOService datasetIOService;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService ops;

    @Parameter
    private ROIService roiService;

    @Parameter(label = "Batch File Location: ")
    public File filePath;

    @Parameter(label = "Model Path: ")
    public File modelpath;

    @Parameter(label = "Cellpose Environment Path: ", style = "directory")
    public File envpath;
    
    RoiManager roiManager;
    double pixelSize;

    String filename;
    @Override
    public void run() {

            roiManager = new RoiManager();
            String [] lines = null;
            try {
				lines = readfile();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
            
            for (int b=0;b<lines.length;b++) {
            	
            	String[] convertVals = lines[b].split("\\t+");
                //Open file and get filename and filepath
                Img<T> img = openDataset(convertVals[0]);
                ImagePlus imp = ImageJFunctions.wrap(img,"Title");
 
               int zPosition = Integer.parseInt(convertVals[1]);
               ImagePlus[] channels = SplitChannelsandGetZ(imp,zPosition);
               filename = convertVals[0];
               int size = 150;
               
               ImagePlus channelToSegment = channels[Integer.parseInt(convertVals[2])];
               channelToSegment.show();
               
               channels[Integer.parseInt(convertVals[2])].setTitle("RED");
               channels[Integer.parseInt(convertVals[3])].setTitle("GREEN");
               RoiManager rm = RoiManager.getRoiManager();
               rm.reset();
               Cellpose_Wrapper cpw = new Cellpose_Wrapper(modelpath.getPath(), envpath.getPath(), size, channelToSegment);
               cpw.run(true);
               ImagePlus regions = WindowManager.getCurrentImage();
        
               ColocNuclei calculateColocalisation = new ColocNuclei(regions,filename);
               calculateColocalisation.run();
               
               IJ.run("Close All", "");
            }
            new WaitForUserDialog("Finished", "Plugin Finished").show();
        }

    private ImagePlus[] SplitChannelsandGetZ(ImagePlus imp, int zPosition) {
    	
    	ImagePlus[] channels = new ImagePlus[5];
    	imp.setZ(zPosition);
    	
    	for (int a=1;a<channels.length;a++) {
    		channels[a] = new Duplicator().run(imp, a, a, zPosition, zPosition, 1, 1);
        	channels[a].show();
        	IJ.run(channels[a], "Enhance Contrast", "saturated=0.35");
    	}
    /*	
    	channels[1] = new Duplicator().run(imp, 1, 1, zPosition, zPosition, 1, 1);
    	channels[1].show();
    	IJ.run(channels[1], "Enhance Contrast", "saturated=0.35");
    	channels[2] = new Duplicator().run(imp, 2, 2, zPosition, zPosition, 1, 1);
    	channels[2].show();
    	IJ.run(channels[2], "Enhance Contrast", "saturated=0.35");
    	channels[3] = new Duplicator().run(imp, 3, 3, zPosition, zPosition, 1, 1);
    	channels[3].show();
    	IJ.run(channels[3], "Enhance Contrast", "saturated=0.35");
    	*/
    	
    	imp.changes=false;
    	imp.close();
    	return channels;
    }

    
    @SuppressWarnings("unchecked")
	public Img<T> openDataset(String dataset) {
            Dataset imageData = null;
            String filePath = dataset;
            try {
                imageData = datasetIOService.open(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Map<String, Object> prop = imageData.getProperties();
            DefaultImageMetadata metaData = (DefaultImageMetadata) prop.get("scifio.metadata.image");
            pixelSize = metaData.getAxes().get(0).calibratedValue(1);
            assert imageData != null;

            return (Img<T>)imageData.getImgPlus();
     }

     public String[] readfile() throws IOException{
    	BufferedReader reader = new BufferedReader( new FileReader (filePath));
    	String line = null;
    	StringBuilder  stringBuilder = new StringBuilder();
    	String ls = System.getProperty("line.separator");

    	while( ( line = reader.readLine() ) != null ) {
    		stringBuilder.append( line );
    		stringBuilder.append( ls );
    	}
    	
    	String[] lines = stringBuilder.toString().split("\\n");
    		
    	//Remove the carriage returns
    	for(int a=0;a<lines.length;a++) {
    		lines[a]=lines[a].replace("\r", "");
    	}
    		
    	reader.close();
   
    	return lines;
    }



    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run(Nucleus_Coloc.class, true);
    }

}
