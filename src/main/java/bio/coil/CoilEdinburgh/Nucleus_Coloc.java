/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package bio.coil.CoilEdinburgh;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import io.scif.*;
import io.scif.bf.BioFormatsFormat;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import loci.common.Location;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imagej.roi.ROIService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.MaskInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.apache.commons.io.FilenameUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import java.io.IOException;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * This example illustrates how to create an ImageJ {@link Command} plugin.
 * <p>
 * </p>
 * <p>
 * You should replace the parameter fields with your own inputs and outputs,
 * and replace the {@link run} method implementation with your own logic.
 * </p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>Users Plugins>Nucleus Colocalisation")
public class Nucleus_Coloc<T extends RealType<T>> implements Command {
    //
    // Feel free to add more parameters here...
    //
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

    @Parameter(label = "Open Folder: ", style="directory")
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

            File[] files = filePath.listFiles();
            roiManager = new RoiManager();
           
            for (File file : files) {
                if (file.toString().contains(".czi") && !file.toString().contains(".czi ")) {
                    //Open file and get filename and filepath
                    Img<T> img = openDataset(file);
              //      uiService.show(img);
                    ImagePlus imp = ImageJFunctions.wrap(img,"Title");
                    imp.show();
                    IJ.run(imp, "Enhance Contrast", "saturated=0.35");
                    new WaitForUserDialog("Select Position", "Move the slider to select the correct Z position").show();
                    int zPosition = imp.getZ();
              
                    
                    ImagePlus[] channels = SplitChannelsandGetZ(imp,zPosition);
                    
                    filename = FilenameUtils.removeExtension(file.getName());
            //        String model = " model_path= "+modelpath.toString();
                    int size = 85;
                    
                    Cellpose_Wrapper cpw = new Cellpose_Wrapper(modelpath.getPath(), envpath.getPath(), size, channels[3]);
                    cpw.run(true);
               
            //        getROIsfromMask();
            //        Roi[] outlines = roiManager.getRoisAsArray();
            //        roiManager.reset();
                    ImagePlus regions = WindowManager.getCurrentImage();
            //        temp.changes=false;
             //       temp.close();

                    ColocNuclei calculateColocalisation = new ColocNuclei(regions, channels);

                 

                    ImagePlus output = ImageJFunctions.wrap(img, "Output");
                    output.show();
                 
                    IJ.save(output, Paths.get(String.valueOf(filePath), filename + "_Overlay.tif").toString());
                    IJ.run("Close All", "");
                }
            }
        }

    private ImagePlus[] SplitChannelsandGetZ(ImagePlus imp, int zPosition) {
    	ImagePlus[] channels = new ImagePlus[4];
    	imp.setZ(zPosition);
    	
    	channels[1] = new Duplicator().run(imp, 1, 1, zPosition, zPosition, 1, 1);
    	channels[1].show();
    	channels[1].setTitle("RED");
    	IJ.run(channels[1], "Enhance Contrast", "saturated=0.35");
    	channels[2] = new Duplicator().run(imp, 2, 2, zPosition, zPosition, 1, 1);
    	channels[2].show();
    	channels[2].setTitle("GREEN");
    	IJ.run(channels[2], "Enhance Contrast", "saturated=0.35");
    	channels[3] = new Duplicator().run(imp, 3, 3, zPosition, zPosition, 1, 1);
    	channels[3].show();
    	channels[3].setTitle("DAPI");
    	IJ.run(channels[3], "Enhance Contrast", "saturated=0.35");
    	imp.changes=false;
    	imp.close();
    	return channels;
    }
    
    
    private void drawNumbers(int Counter, ImagePlus ProjectedWindow, Roi roi) {
        ImageProcessor ip = ProjectedWindow.getProcessor();
        Font font = new Font("SansSerif", Font.PLAIN, 12);
        ip.setFont(font);
        ip.setColor(Color.white);
        String cellnumber = String.valueOf(Counter);
        ip.draw(roi);
        ip.drawString(cellnumber, (int) roi.getContourCentroid()[0], (int) roi.getContourCentroid()[1]);
        ProjectedWindow.updateAndDraw();
    }


    public void MakeResults(boolean[] budding, Roi[] outlines2) throws IOException {
        Date date = new Date(); // This object contains the current date value
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy, hh:mm:ss");
        String CreateName = Paths.get(String.valueOf(filePath), "_Results.csv").toString();
        try {
            FileWriter fileWriter = new FileWriter(CreateName, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.newLine();
            bufferedWriter.write(formatter.format(date));
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.write("File, Number, Area_(um^2), LongAxis_(um), Short_Axis_(um), Circularity, Budding");//write header 1
            bufferedWriter.newLine();
            for (int i =0; i < outlines2.length; i++){//for each slice create and write the output string
                bufferedWriter.newLine();
                double circularity = 4 * Math.PI * outlines2[i].getStatistics().area/(outlines2[i].getLength()*outlines2[i].getLength());
                bufferedWriter.write(filename+ ","+(i+1)+","+outlines2[i].getStatistics().area*pixelSize*pixelSize+","+ outlines2[i].getFeretValues()[0]*
                        pixelSize+","+ outlines2[i].getFeretValues()[2]*pixelSize+","+circularity+","+Boolean.toString(budding[i]));
            }
            bufferedWriter.close();
        } catch (IOException ex) {
            System.out.println(
                    "Error writing to file '" + CreateName + "'");
        }
    }

    //Adds the Masks created by cellpose to the ROI manager
    public void getROIsfromMask() {

        //Gets the current image (the mask output from cellpose)
        ImagePlus mask = WindowManager.getCurrentImage();
        ImageStatistics stats = mask.getStatistics();
        //For each ROI (intensity per cell mask is +1 to intensity
        for (int i = 1; i < stats.max + 1; i++) {
            //Set the threshold for the cell and use analyse particles to add to ROI manager
            IJ.setThreshold(mask, i, i);
            IJ.run(mask, "Analyze Particles...", "add");
        }
    }

        public Img<T> openDataset(File dataset) {
            Dataset imageData = null;
            String filePath = dataset.getPath();
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
