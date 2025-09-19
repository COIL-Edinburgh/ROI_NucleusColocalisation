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
import ij.io.FileSaver;
import ij.process.ImageStatistics;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles running Cellpose
 */
public class CellposeWrapper {
    private final ImagePlus img;
    private final String modelPath;
    private final String envPath;
    private final int diameter;


    public CellposeWrapper(String modelPath, String envPath, int diameter, ImagePlus img) {
        this.modelPath = modelPath;
        this.envPath = envPath;
        this.diameter = diameter;
        this.img = img;
    }

    TempDirectory tempDir;

    /**
     * Run Cellpose on the provided image and return masks and RoIs
     * @param ROIs generate RoIs from Cellpose masks?
     * @return Cellpose masks image
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public ImagePlus run(boolean ROIs) {
        // Get and clean a temporary directory
        tempDir = new TempDirectory("temp");
        tempDir.purge(false);
        String tempDirPath = tempDir.getPath().toAbsolutePath().toString();
        // Save image to file so Cellpose can use it
        new FileSaver(img).saveAsTiff(Paths.get(tempDirPath, "Temp.tif").toString());

        // Construct commands for Cellpose
        List<String> cmd = makeCommands(diameter, modelPath);

        // Send commands to Cellpose
        try {
            runCommands(cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Read masks image produced by Cellpose
        ImagePlus masks = IJ.openImage(Paths.get(tempDirPath, "Temp_cp_masks.tif").toString());

        if (ROIs) {
            masks.show();
            getROIsFromMask(masks);
        }

        tempDir.purge(true);
        return masks;

    }

    /**
     * Produce RoIs from Cellpose masks image
     * Cellpose masked cells have brightness ranging from 1 to max brightness,
     * increasing by 1 per region from top-left to bottom-right
     * @param mask masks image from Cellpose
     */
    private void getROIsFromMask(ImagePlus mask) {
        mask.show();
        ImageStatistics stats = mask.getStatistics();
        // For each ROI (intensity per cell mask is +1 to intensity)
        for (int i = 1; i < stats.max + 1; i++) {
            // Set the threshold for the cell and use analyse particles to add to ROI manager
            IJ.setThreshold(mask, i, i);
            IJ.run(mask, "Analyze Particles...", "exclude add");
        }
    }

    /**
     * Prepare command line strings for executing Cellpose
     * @param diameter expected cell diameter (potentially unused by CPSAM, but we need to provide it anyway?)
     * @param model path to pretrained model (`cpsam` file)
     * @return commands to execute Cellpose
     */
    private List<String> makeCommands(int diameter, String model) {
        List<String> runWithCMD = Arrays.asList("cmd.exe", "/C");
        List<String> activateCondaEnv = Arrays.asList("CALL", "conda.bat", "activate", envPath);
        List<String> runCellpose = Arrays.asList("python", "-Xutf8", "-m", "cellpose");
        List<String> cellposeCommonOpts = Arrays.asList("--diameter", diameter + "", "--verbose", "--pretrained_model", model, "--save_tif", "--use_gpu", "--dir", tempDir.getPath().toString());
        List<String> cellpose2channelImgOpts = Arrays.asList("--chan", "2", "--chan2", "1");

        // Assemble commands
        List<String> cmd = new ArrayList<>(runWithCMD);
        cmd.addAll(activateCondaEnv);
        cmd.add("&");
        cmd.addAll(runCellpose);
        cmd.addAll(cellposeCommonOpts);
        if (img.getNChannels() == 2) {
            cmd.addAll(cellpose2channelImgOpts);
        }
        return cmd;
    }

    /**
     * Encapsulates a temporary directory
     */
    public static class TempDirectory {
        final Path path;

        public TempDirectory(String prefix) {
            try {
                path = Files.createTempDirectory(prefix);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Path getPath() {
            return path;
        }

        public void purge(boolean deleteSelf) {
            File self = path.toFile();
            try {
                if (deleteSelf) {
                    FileUtils.deleteDirectory(self);
                } else {
                    FileUtils.cleanDirectory(self);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Runs the list of commands provided and echoes the output
     * @param cmd list of commands to run
     * @throws Exception if we can't set up the process pipe
     */
    public void runCommands(List<String> cmd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        // Capture standard error and standard output
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        // Echo output
        for (String line = r.readLine(); line != null; line = r.readLine()) {
            System.out.println(line);
        }
    }
}
