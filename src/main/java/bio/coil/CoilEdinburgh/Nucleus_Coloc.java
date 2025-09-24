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

import bio.coil.CoilEdinburgh.ColocNuclei.AutoThresholdAlgorithm;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;
import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;

import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.roi.ROIService;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public File batchFilePath;

    @Parameter(label = "Model Path: ")
    public File modelPath;

    @Parameter(label = "Cellpose Environment Path: ", style = "directory")
    public File envPath;

    @Parameter(label = "Pearson's Auto-Threshold Algorithm: ", choices = {"Costes", "Bisection"})
    public String autoThresholdAlgorithm;

    RoiManager roiManager;

    private static int parseChannelIndex(String channelIndex, int maxChannels) {
        int channelIdx = Integer.parseInt(channelIndex);
        if (channelIdx <= 0 || channelIdx > maxChannels) {
            throw new IllegalArgumentException("Channel index out of bounds: " + channelIdx + ", must be between 1 and " + maxChannels);
        }
        return channelIdx;
    }

    private static int parseZPos(String zPosition, int maxSlices) {
        int zPos = Integer.parseInt(zPosition);
        if (zPos <= 0 || zPos > maxSlices) {
            throw new IllegalArgumentException("Z position out of bounds: " + zPos + ", must be between 1 and " + maxSlices);
        }
        return zPos;
    }

    public static CSVFormat.Builder getTsvBaseFormat() {
        return CSVFormat.TDF.builder()
                .setEscape(null)
                .setQuote(null)
                .setIgnoreEmptyLines(true);
    }

    @SuppressWarnings("unchecked")
	@Override
    public void run() {

        roiManager = new RoiManager();
        try (Reader batchFileReader = new FileReader(batchFilePath)) {
            Iterable<CSVRecord> batchJobDetails = getTsvBaseFormat()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .get()
                    .parse(batchFileReader);
            for (CSVRecord jobDetail : batchJobDetails) {
                Path imageFilePath = Paths.get(jobDetail.get("imageFilePath"));
                if (!imageFilePath.toFile().exists()) {
                    throw new IllegalArgumentException("File does not exist: " + imageFilePath.toAbsolutePath());
                }
                Img<T> imp_temp = (Img<T>) datasetIOService.open(imageFilePath.toString()).getImgPlus();
                String title = imageFilePath.getFileName().toString();
                ImagePlus imp = ImageJFunctions.wrap(imp_temp, title);

                ImagePlus[] channels = SplitChannelsAndGetZ(imp, parseZPos(jobDetail.get("zSlice"), imp.getNSlices()));

                int maxChannels = imp.getNChannels();
                int segmentationChannelIdx = parseChannelIndex(jobDetail.get("segmentationChannelIdx"), maxChannels);
                int comparisonChannelIdx = parseChannelIndex(jobDetail.get("comparisonChannelIdx"), maxChannels);

                ImagePlus channelToSegment = channels[segmentationChannelIdx];

                String segmentationChannelName = jobDetail.get("segmentationChannelName");
                String comparisonChannelName = jobDetail.get("comparisonChannelName");

                channels[segmentationChannelIdx].setTitle(segmentationChannelName);
                channels[comparisonChannelIdx].setTitle(comparisonChannelName);

               // roiManager=RoiManager.getRoiManager();
              //  Roi[] outlines = roiManager.getRoisAsArray();  //Assign the identified nuclei to an ROI array
                Roi[] outlines = FindNuclei(channels);
                
         //       RoiManager rm = RoiManager.getRoiManager();
          //      rm.reset();
                int size = 150;
                CellposeWrapper cpw = new CellposeWrapper(modelPath.getPath(), envPath.getPath(), size, channelToSegment);
                @SuppressWarnings("unused")
				ImagePlus ignored = cpw.run(true);
           //     ImagePlus regions = WindowManager.getCurrentImage();
                getROIsfromMask();
                roiManager=RoiManager.getRoiManager();
                Roi[] regions  = roiManager.getRoisAsArray();  //Assign the identified nuclei to an ROI array
                roiManager.reset();
                ImagePlus maskImage = WindowManager.getCurrentImage();
                
                ColocNuclei calculateColocalisation = new ColocNuclei(
                        regions, imageFilePath,
                        segmentationChannelName, comparisonChannelName,
                        AutoThresholdAlgorithm.valueOf(autoThresholdAlgorithm), outlines, maskImage);
                calculateColocalisation.run();

                IJ.run("Close All", "");
            }
            new WaitForUserDialog("Finished", "Plugin Finished").show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Roi[] FindNuclei(ImagePlus[] channels) {
    	
    	Roi[] outlines = null;
    	int size =150;
    	ImagePlus dapiChannel = channels[1];
    	CellposeWrapper cpw = new CellposeWrapper(modelPath.getPath(), envPath.getPath(), size,  dapiChannel);
        ImagePlus nuc = cpw.run(true);
    	
        getROIsfromMask();
        roiManager=RoiManager.getRoiManager();
        outlines = roiManager.getRoisAsArray();  //Assign the identified nuclei to an ROI array
        roiManager.reset();
        nuc.changes= false;
        nuc.close();
    	return outlines;
    }
    
  //Adds the Masks created by cellpose to the ROI manager
    public void getROIsfromMask() {

        //Gets the current image (the mask output from cellpose)
        ImagePlus mask = WindowManager.getCurrentImage();
        ImageStatistics stats = mask.getStatistics();
        RoiManager rm = RoiManager.getRoiManager();
        rm.reset();
        //For each ROI (intensity per cell mask is +1 to intensity
        for (int i = 1; i < stats.max + 1; i++) {
            //Set the threshold for the cell and use analyse particles to add to ROI manager
            IJ.setThreshold(mask, i, i);
            IJ.run(mask, "Analyze Particles...", "add");
        }
    }
    
    private ImagePlus[] SplitChannelsAndGetZ(ImagePlus imp, int zPosition) {
        ImagePlus[] channels = new ImagePlus[imp.getNChannels() + 1];
        if (zPosition <= 0 || zPosition > imp.getNSlices()) {
            throw new IllegalArgumentException(String.format("zPosition out of bounds - should be from 1 to %d", imp.getNSlices()));
        }
        imp.setZ(zPosition);

        for (int channel = 1; channel < channels.length; ++channel) {
            channels[channel] = new Duplicator().run(imp, channel, channel, zPosition, zPosition, 1, 1);
            channels[channel].show();
            IJ.run(channels[channel], "Enhance Contrast", "saturated=0.35");
        }
        imp.changes = false;
        imp.close();
        return channels;
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     */
    public static void main(final String... args) {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run(Nucleus_Coloc.class, true);
    }

}
