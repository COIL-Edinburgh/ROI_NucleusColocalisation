# ImageJ2 plugin implementing co-localisation within Cellpose-defined RoIs

## Dependencies
1. [Cellpose SAM](https://github.com/MouseLand/cellpose) 
2. SciJava libraries (shipped with Fiji)
3. [Apache Commons CSV](https://repo1.maven.org/maven2/org/apache/commons/commons-csv/1.14.0/commons-csv-1.14.0.jar)
4. [Apache Commons IO](https://repo1.maven.org/maven2/commons-io/commons-io/2.17.0/commons-io-2.17.0.jar)

## Build instructions

1. Install Cellpose and all requirements
2. Ensure the `cpsam` file is present in the `%USERDATA%/.cellpose/models` folder (this might need you to run Cellpose on a test TIF image) 
3. Run the Maven lifecycle `package` task
4. Download the Apache Commons IO and CSV `.jar` files from the links above
5. Copy them to  the `Fiji.app/jars/` folder
6. Copy the `Nucleus_Coloc-1.1.1.jar` file from the `target` folder to the `Fiji.app/plugins/` folder
7. Reload the Fiji menus 
8. The co-localisation plugin should show up under `Plugins` > `Users Plugins` > `Nucleus Colocalisation`
9. Specify the path to the locally installed Cellpose environment in the opened dialog window
   - For `conda` installs this is within the `env` folder of the `conda` installation folder
10. Specify path to input batch file (see next section for format)
11. Specify the thresholding mode to use - `Bisection` worked better for calculating `Pearson's R` values above threshold 
## Data input format

Text file with tab separated values and the header as below:

Header:

`imageFilePath	zSlice	segmentationChannelIdx	segmentationChannelName	comparisonChannelIdx	comparisonChannelName`

Each row should then specify:

`<full file path to .czi image file>    <slice in Z-stack to analyse (1-based)> <index of channel to use for segmenting cells (1-based)>	<name of channel to use for segmenting cells (arbitrary)> <index of channel to use for co-localisation comparison with segmenting channel (1-based)>	<name of channel to use for co-localisation comparison (arbitrary)>`

## Data output format

For each combination of image file and channels, 2 results files will be produced in the same directory as the input image file:

- `<image_file>.czi_Overlay.tif`
  - Cellpose-defined RoIs for ImageJ with RoI IDs
- `<image_file>.czi_results_<segmentationChannelName>_<comparisonChannelName>.tsv`
  - Tab-separated values file in the below format:
    - Header:
      - `Cell	Pearson's R (No threshold)	Pearson's R (Below threshold)	Pearson's R (Above threshold)	Spearman's R	<Segmentation> channel (C1) Costes auto threshold	<Comparison> channel (C2) Costes auto threshold	Costes p-value	Manders' tM1 (above <Comparison> channel (C2) threshold)	Manders' tM2 (above <Segmentation> channel (C1) threshold)`
    - Each row (for all numbered RoIs in the `_Overlay.tif` file):
      - `<RoI ID as in the overlay.tif file>	<Pearson's R (No threshold)>	<Pearson's R (Below threshold)>	<Pearson's R (Above threshold)>	<Spearman's R>	<Segmentation channel Costes auto threshold>	<Comparison channel Costes auto threshold>	<Costes p-value>	<Manders' tM1>	<Manders' tM2>`
