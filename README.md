# ImageJ2 plugin implementing co-localisation within Cellpose-defined RoIs

## Dependencies
1. [Cellpose SAM](https://github.com/MouseLand/cellpose) 
2. SciJava libraries (shipped with Fiji)
3. [Apache Commons CSV](https://repo1.maven.org/maven2/org/apache/commons/commons-csv/1.14.0/commons-csv-1.14.0.jar)
4. [Apache Commons IO](https://repo1.maven.org/maven2/commons-io/commons-io/2.17.0/commons-io-2.17.0.jar)

### Plugin Installation

1. Ensure that the computer has a working version of Cellpose (SAM).

2. The Cellpose installation guide can be found here https://github.com/MouseLand/cellpose

3. Download and install the latest version of FIJI fro here https://imagej.net/software/fiji/

4. Copy the plugin .jar file into the plugins directory of the FIJI installation.

5. Restart FIJI.

6. If the plugin has installed correctly then the plugin "Nucleus Colocalisation" will appear in the plugins list.

### Plugin Usage

1. A .txt batchfile is required to set up how the colocalisation will work and which images to use.

2. An example batchfile is included on github

3. batchfile construction
	(a) imageFilePath - This is the filepath location to the image.
	(b) zSlice - The z position to use for the co-localisation calculations.
	(c) segmentationChannelIdx - The channel number in FIJI containing channel 1 for co-localisation (eg GFP in channel 2).
	(d) segmentationChannelName - This is the name that will be applied to channel 1.
	(e) comparisonChannelIdx - The channel number in FIJI containing channel 2 for co-localisation (eg RFP in channel 3).
	(f) comparisonChannelName - This is the name that will be applied to channel 2.
	
	The plugin will measure as many files as you wish in the order they are placed in the batchfile. 

4. Select Nucleus Colocalisation from FIJI's plugins

5. A dialogue box titled Nucleus Colocalisation will appear wth various options.
	(a) Batch File Location - Use Browse to navigate to the batchfile.txt containing the information about your files and their location.
	(b) Model Path - The location of the Cellpose model in use. The plugin was developed using the model cpsam.
	(c) Cellpose Environment Path - Browse to the location of where you installed your cellpose environment.
	(d) Pearsons Auto-Threshold Algorithm - The choices are Costes or Bicubic and are used to determine the threshold at which measurements will be performed. 

6. The plugin will run and output an image overlayed with the cell numbers so that results can be matched with cells.

7. A tab separated file with all the colocalisation results is output to the same directory as the image file.

