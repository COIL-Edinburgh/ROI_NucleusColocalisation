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

    public ImagePlus run(boolean ROIs) {

        tempDir = new TempDirectory("temp");
        tempDir.purge(false);
        String tempDirPath = tempDir.getPath().toAbsolutePath().toString();
        new FileSaver(img).saveAsTiff(Paths.get(tempDirPath, "Temp.tif").toString());

        //Construct Commands for cellpose
        List<String> cmd = makeCommands(diameter, modelPath);

        //Send to cellpose
        try {
            runCommands(cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //return image
        ImagePlus masks = IJ.openImage(Paths.get(tempDirPath, "Temp_cp_masks.tif").toString());

        if (ROIs) {
            masks.show();
            getROIsFromMask(masks);
        }

        tempDir.purge(true);
        return masks;

    }

    private void getROIsFromMask(ImagePlus mask) {
        mask.show();
        ImageStatistics stats = mask.getStatistics();
        //For each ROI (intensity per cell mask is +1 to intensity
        for (int i = 1; i < stats.max + 1; i++) {
            //Set the threshold for the cell and use analyse particles to add to ROI manager
            IJ.setThreshold(mask, i, i);
            IJ.run(mask, "Analyze Particles...", "exclude add");
        }
    }

    private List<String> makeCommands(int diameter, String model) {
        List<String> start_cmd = Arrays.asList("cmd.exe", "/C");
        List<String> condaCmd = Arrays.asList("CALL", "conda.bat", "activate", envPath);
        List<String> cellpose_args_cmd = Arrays.asList("python", "-Xutf8", "-m", "cellpose");
        List<String> options = Arrays.asList("--diameter", diameter + "", "--verbose", "--pretrained_model", model, "--save_tif", "--use_gpu", "--dir", tempDir.getPath().toString());
        List<String> options2 = Arrays.asList("--chan", "2", "--chan2", "1");

        List<String> cmd = new ArrayList<>(start_cmd);
        cmd.addAll(condaCmd);
        cmd.add("&");
        cmd.addAll(cellpose_args_cmd);
        cmd.addAll(options);
        if (img.getNChannels() == 2) {
            cmd.addAll(options2);
        }
        return cmd;
    }

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

    public void runCommands(List<String> cmd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        for (String line = r.readLine(); line != null; line = r.readLine()) {
            System.out.println(line);
        }
    }
}
