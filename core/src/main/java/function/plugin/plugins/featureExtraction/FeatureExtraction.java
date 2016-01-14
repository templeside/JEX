// Define package name as "plugins" as show here
package function.plugin.plugins.featureExtraction;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.scijava.plugin.Plugin;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataReader.RoiReader;
import Database.DataWriter.FileWriter;
import Database.SingleUserDatabase.JEXReader;
import function.plugin.IJ2.IJ2PluginUtility;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;
// Import needed classes here 
import image.roi.IdPoint;
import image.roi.ROIPlus;
import io.scif.img.ImgOpener;
import jex.statics.JEXDialog;
import jex.statics.JEXStatics;
import logs.Logs;
import miscellaneous.JEXCSVReader;
import miscellaneous.JEXCSVWriter;
import miscellaneous.Pair;
import net.imagej.ops.features.sets.Geometric2DFeatureSet;
import net.imagej.ops.features.sets.Haralick2DFeatureSet;
import net.imagej.ops.features.sets.HistogramFeatureSet;
import net.imagej.ops.features.sets.ImageMomentsFeatureSet;
import net.imagej.ops.features.sets.StatsFeatureSet;
import net.imagej.ops.features.sets.ZernikeFeatureSet;
import net.imagej.ops.featuresets.NamedFeature;
import net.imagej.ops.image.cooccurrencematrix.MatrixOrientation2D;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.PositionableIterableRegion;
import net.imglib2.roi.Regions;
import net.imglib2.roi.geometric.Polygon;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import tables.DimTable;
import tables.DimTableBuilder;
import tables.DimensionMap;
import weka.core.converters.JEXTableWriter;

// Specify plugin characteristics here
@Plugin(
		type = JEXPlugin.class,
		name = "Feature Extraction",
		menuPath = "Feature Extraction",
		visible = true,
		description = "Function for testing feature extraction using the ImageJ Ops framework."
		)
public class FeatureExtraction<T extends RealType<T>> extends JEXPlugin {

	public ImgOpener imgOpener;

	public Geometric2DFeatureSet<Polygon, DoubleType> opGeometric = null;
	public Haralick2DFeatureSet<T, DoubleType> opHaralick2DHor = null;
	public Haralick2DFeatureSet<T, DoubleType> opHaralick2DVer = null;
	public Haralick2DFeatureSet<T, DoubleType> opHaralick2DDiag = null;
	public Haralick2DFeatureSet<T, DoubleType> opHaralick2DAntiDiag = null;
	public HistogramFeatureSet<T> opHistogram = null;
	public ImageMomentsFeatureSet<T, DoubleType> opMoments = null;
	// What about IntensityFeatureSet???
	public StatsFeatureSet<T, DoubleType> opStats = null;
	// TODO: Figure out how to get a RandomAccessbleInterval<T> from a
	// LabelRegion and an Img<T>
	// public Tamura2DFeatureSet<T,DoubleType> opTamura = null;
	public ZernikeFeatureSet<BitType> opZernike = null;

	public JEXCSVWriter writer;
	public Set<String> header = null;

	public int total = 0, count = 0;
	public int percentage = 0;

	public TreeMap<DimensionMap, String> imageMap = new TreeMap<DimensionMap, String>();
	public TreeMap<DimensionMap, String> maskMap = new TreeMap<DimensionMap, String>();
	public TreeMap<DimensionMap, ROIPlus> roiMap = new TreeMap<DimensionMap, ROIPlus>();
	TreeMap<Integer, Integer> idToLabelMap = new TreeMap<Integer, Integer>();
	
	FeatureUtils utils = new FeatureUtils();

	// Define a constructor that takes no arguments.
	public FeatureExtraction() {
	}

	// ///////// Define Inputs here ///////////

	@InputMarker(uiOrder = 1, name = "Images to Measure", type = MarkerConstants.TYPE_IMAGE, description = "Intensity images", optional = false)
	JEXData imageData;

	@InputMarker(uiOrder = 3, name = "Masks", type = MarkerConstants.TYPE_IMAGE, description = "Mask images", optional = false)
	JEXData maskData;

	@InputMarker(uiOrder = 4, name = "Maxima", type = MarkerConstants.TYPE_ROI, description = "Maxima ROI", optional = false)
	JEXData roiData;

	// ///////// Define Parameters here ///////////

	@ParameterMarker(uiOrder = 0, name = "Channel dim name", description = "Channel dimension name in mask data.", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "Channel")
	String channelName;

	@ParameterMarker(uiOrder = 1, name = "'Whole Cell' mask channel value", description = "Which channel value of the mask image represents the whole cell that has a 1-to-1 mapping with the maxima points.", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "WholeCell")
	String maskWholeCellChannelValue;

	@ParameterMarker(uiOrder = 2, name = "Image intensity offset", description = "Amount the images are offset from zero (will be subtracted before calculation)", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "0.0")
	double offset;

	@ParameterMarker(uiOrder = 3, name = "** Compute Stats Features?", description = "Whether to quantify first order statistics", ui = MarkerConstants.UI_CHECKBOX, defaultBoolean = true)
	boolean stats;

	@ParameterMarker(uiOrder = 4, name = "** Compute 2D Geometric Features?", description = "Whether to quantify geometric statistics", ui = MarkerConstants.UI_CHECKBOX, defaultBoolean = false)
	boolean geometric;

	@ParameterMarker(uiOrder = 5, name = "** Compute 2D Haralick Features?", description = "Whether to quantify Haralick texture statistics", ui = MarkerConstants.UI_CHECKBOX, defaultBoolean = false)
	boolean haralick2D;

	@ParameterMarker(uiOrder = 6, name = "** Compute Histogram Features?", description = "Whether to quantify histogram statistics", ui = MarkerConstants.UI_CHECKBOX, defaultBoolean = false)
	boolean histogram;

	@ParameterMarker(uiOrder = 7, name = "** Compute Moments Features?", description = "Whether to quantify image moment statistics", ui = MarkerConstants.UI_CHECKBOX, defaultBoolean = false)
	boolean moments;

	// @ParameterMarker(uiOrder = 5, name = "** Compute 2D Tamura Features?",
	// description = "Whether to quantify Tamura statistics", ui =
	// MarkerConstants.UI_CHECKBOX, defaultBoolean = false)
	// boolean tamura;

	@ParameterMarker(uiOrder = 8, name = "** Compute Zernike Features?", description = "Whether to quantify Zernike shape statistics", ui = MarkerConstants.UI_CHECKBOX, defaultBoolean = false)
	boolean zernike;

	@ParameterMarker(uiOrder = 9, name = "** Connectedness Features", description = "The structuring element or number of neighbors to require to be part of the neighborhood.", ui = MarkerConstants.UI_DROPDOWN, choices = {
			"4 Connected", "8 Connected" }, defaultChoice = 0)
	String connectedness;

	@ParameterMarker(uiOrder = 10, name = "Haralick Gray Levels", description = "Number of gray levels for Haralick calculations", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "8")
	int haralickGrayLevels;

	@ParameterMarker(uiOrder = 11, name = "Haralick Co-Occurrence Matrix Distance", description = "Distance at which to compute the co-occurrence matrix", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "1")
	double haralickDistance;

	@ParameterMarker(uiOrder = 12, name = "Haralick Number of Directions", description = "(Orthogonals and Diagonals etc) 2 performs horizontal and vertical. 4 adds the 2 diagonals as well.", ui = MarkerConstants.UI_DROPDOWN, choices = {
			"2", "4" }, defaultChoice = 1)
	String haralickNumDirections;

	@ParameterMarker(uiOrder = 13, name = "Histogram Number of Bins", description = "Number of bins for the histogram created for each cell region", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "256")
	int histogramBins;

	@ParameterMarker(uiOrder = 14, name = "Tamura 2D Number of Bins", description = "Number of bins for the histogram created for each cell region for the directionality feature", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "256")
	int tamuraBins;

	@ParameterMarker(uiOrder = 15, name = "Zernike Min Moment", description = "Min Zernike moment calculate", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "1")
	int zernikeMomentMin;

	@ParameterMarker(uiOrder = 16, name = "Zernike Max Moment", description = "Max Zernike moment calculate", ui = MarkerConstants.UI_TEXTFIELD, defaultText = "3")
	int zernikeMomentMax;

	// Add a primary mask and secondary mask

	/////////// Define Outputs here ///////////

	@OutputMarker(uiOrder = 1, name = "Output CSV Table", type = MarkerConstants.TYPE_FILE, flavor = "", description = "Output in csv format (i.e., for Excel etc).", enabled = true)
	JEXData outputCSV;

	@OutputMarker(uiOrder = 1, name = "Output ARFF Table", type = MarkerConstants.TYPE_FILE, flavor = "", description = "Test table output (i.e., for Weka etc).", enabled = true)
	JEXData outputARFF;

	// Define threading capability here (set to 1 if using non-final static
	// variables shared between function instances).
	@Override
	public int getMaxThreads() {
		return 1;
	}

	// Code the actions of the plugin here using comments for significant
	// sections of code to enhance readability as shown here
	@Override
	public boolean run(JEXEntry optionalEntry) {

		if (!stats && !geometric && !haralick2D && !histogram && !moments && !zernike) {
			JEXDialog.messageDialog("Feature Extraction: Nothing selected to compute. Returning false.");
			return false;
		}
		if (maskData == null) {
			JEXDialog.messageDialog("Feature Extraction: Returning false. NEED to have a mask.");
			return false;
		}
		if (roiData == null) {
			JEXDialog.messageDialog(
					"Feature Extraction: Returning false. NEED to have a roi that defines the id of each cell.");
			return false;
		}
		if (imageData == null && (stats || haralick2D || histogram || moments)) {
			JEXDialog.messageDialog(
					"Feature Extraction: Returning false. NEED to define an image to quantify if you want to use intensity based features such as first order, haralick2D, histogram, and moment statistics.");
			return false;
		}

		if ((imageData != null && !imageData.getTypeName().getType().equals(JEXData.IMAGE))
				|| (maskData != null && !maskData.getTypeName().getType().equals(JEXData.IMAGE))
				|| (roiData != null && !roiData.getTypeName().getType().equals(JEXData.ROI))) {
			JEXDialog.messageDialog(
					"All inputs to the function are not of the correct 'Type'. Please check that the image and mask object are 'Image' objects and the maxima is a 'Roi' object.",
					this);
			return false;
		}

		imageMap = new TreeMap<>();
		if (imageData != null) {
			imageMap = ImageReader.readObjectToImagePathTable(imageData);
		}
		maskMap = ImageReader.readObjectToImagePathTable(maskData);
		roiMap = RoiReader.readObjectToRoiMap(roiData);

		this.count = 0;
		this.percentage = 0;

		// Calculate expected number of iterations
		// Assume at least calculating mask features
		this.total = maskData.getDimTable().mapCount();
		// Recalculate total if also calculating intensity features
		if (imageData != null && (stats || haralick2D || histogram || moments)) {
			this.total = maskData.getDimTable().mapCount()
					+ maskData.getDimTable().mapCount() * imageData.getDimTable().mapCount();
		}

		DimTable noMaskChannelTable = maskData.getDimTable().getSubTable(channelName);
		for (DimensionMap noMaskChannelMap : noMaskChannelTable.getMapIterator()) {
			DimensionMap mapWholeCellMask = noMaskChannelMap.copyAndSet(channelName + "=" + maskWholeCellChannelValue);
			Logs.log("Getting whole cell mask: " + mapWholeCellMask, this);
			Img<UnsignedByteType> wholeCellMaskImage = JEXReader.getByteImage(maskMap.get(mapWholeCellMask));

			if (this.isCanceled()) {
				this.close();
				return true;
			}

			ImgLabeling<Integer, IntType> cellLabeling = utils.getConnectedComponents(wholeCellMaskImage, connectedness.equals("4 Connected"));
			LabelRegions<Integer> cellRegions = new LabelRegions<Integer>(cellLabeling);
			idToLabelMap = new TreeMap<Integer, Integer>();

			// Determine which LabelRegions are the ones we want to keep by
			// testing if our maxima of interest are contained.
			ROIPlus maxima = roiMap.get(mapWholeCellMask);
			for (LabelRegion<Integer> cellRegion : cellRegions) {
				if (this.isCanceled()) {
					this.close();
					return true;
				}
				RandomAccess<BoolType> ra = Regions.iterable(cellRegion).randomAccess();
				for (IdPoint p : maxima.pointList) {
					ra.setPosition(p);
					if (ra.get().get()) {
						idToLabelMap.put(p.id, cellRegion.getLabel().intValue());
					}
				}
			}

			// for each map matching this subMap (now we are looping over mask
			// channel)
			for (DimensionMap mapMask : maskData.getDimTable().getSubTable(noMaskChannelMap).getMapIterator()) {
				Logs.log("Getting mask: " + mapMask.toString(), this);
				Img<UnsignedByteType> maskImage = JEXReader.getSingleImage(maskMap.get(mapMask));

				DimTable imageDimTable = new DimTable();
				if (imageData != null) {
					imageDimTable = imageData.getDimTable();
				}

				// Loop over channels of intensity images associated with this
				// subMap
				// FYI: Whether we have any images (vs. masks) or not, we will
				// at least go through this loop once with an empty mapImage
				// This is part of the nature of the DimTable iteration scheme.
				// So we have to watch out for null image paths and
				// intensityImages
				boolean firstTimeThrough = true;
				for (DimensionMap mapImage : imageDimTable.getSubTable(noMaskChannelMap).getMapIterator()) {
					String maskOnImageString = this.getMaskOnImageString(mapMask, mapImage);
					DimensionMap mapMeasure = noMaskChannelMap
							.copyAndSet("MaskChannel_ImageChannel=" + maskOnImageString);
					Logs.log("Measuring: " + mapMeasure.toString(), this);

					Img<T> intensityImage = JEXReader.getSingleImage(imageMap.get(mapImage));

					for (IdPoint p : maxima.pointList) {
						if (this.isCanceled()) {
							this.close();
							return true;
						}
						Integer label = this.idToLabelMap.get(p.id);
						if (label == null) {
							continue;
						}
						LabelRegion<Integer> region = cellRegions.getLabelRegion(label);
						// TODO maybe include subregion count
						// DimensionMap newMap =
						// mapM.copyAndSet("Measurement=subRegionCount");
						// newMap.put("Id", "" + id);
						// newMap.put("Label", "" + idToLabelMap.get(id));
						// this.write(newMap, (double) subRegionCount);
						LabelRegion<Integer> majorSubRegion = getMajorSubRegion(region, maskImage);

						// quantify intensity features first (for a complete CSV
						// header)
						this.quantifyIntensityFeatures(mapMeasure, p.id, majorSubRegion, intensityImage);
						if (firstTimeThrough) {
							// Then quantify geometric features of mask
							DimensionMap temp = noMaskChannelMap
									.copyAndSet("MaskChannel_ImageChannel=" + mapMask.get(channelName) + "_NA");
							this.quantifyGeometricFeatures(temp, p.id, majorSubRegion);
							this.count = this.count + 1;
							this.percentage = (int) (100 * ((double) (count) / ((double) total)));
							JEXStatics.statusBar.setProgressPercentage(percentage);
						}
					}
					this.count = this.count + 1;
					this.percentage = (int) (100 * ((double) (count) / ((double) total)));
					JEXStatics.statusBar.setProgressPercentage(percentage);
					firstTimeThrough = false;
				}
			}

		}

		this.close();

		// Return status
		return true;
	}

	public String getMaskOnImageString(DimensionMap mapMask, DimensionMap mapImage) {

		String imageChannelString = null;
		if (mapImage != null) {
			imageChannelString = mapImage.get(channelName);
		}
		String maskChannelString = null;
		if (mapImage != null) {
			maskChannelString = mapMask.get(channelName);
		}

		if (maskChannelString == null && imageChannelString == null) {
			// Should never happen.
			return "NA_NA";
		}
		if (imageChannelString == null) {
			return maskChannelString + "_NA";
		}
		if (maskChannelString == null) {
			return "NA_" + imageChannelString;
		} else {
			return maskChannelString + "_" + imageChannelString;
		}
	}

	public void write(DimensionMap map, Double value) {
		if (this.writer == null) {
			this.writer = new JEXCSVWriter();
			this.writer.writeHeader(map);
		}
		DimensionMap temp = map.copy();
		if (header == null) {
			header = map.copy().keySet();
		}
		for (String s : header) {
			if (!map.containsKey(s)) {
				temp.put(s, "NA");
			}
		}
		writer.write(temp, value.toString());
	}

	public void close() {
		Logs.log("Closing the function (closing file writers and converting output file to arff as well).", this);
		this.writer.close();
		String csvPath = writer.getPath();
		JEXCSVReader reader = new JEXCSVReader(csvPath, true);

		// In order to write an Arff table we need to build a DimTable
		// We can't keep all the data in memory as it might be too large so just
		// build DimTable for now.
		DimTableBuilder builder = new DimTableBuilder();
		while (!reader.isEOF()) {
			Pair<DimensionMap, String> toAdd = reader.readRowToDimensionMapString();
			if (toAdd != null) {
				builder.add(toAdd.p1);
			} else {
				Logs.log("Went past end of file!?!", this);
			}
		}
		reader.close();

		// Now that we have the DimTable we can transfer each row of the csv to
		// the arff file after writing the header.
		reader = new JEXCSVReader(csvPath, true);
		JEXTableWriter arffWriter = new JEXTableWriter("FeatureTable", "arff");
		arffWriter.writeNumericTableHeader(builder.getDimTable());
		while (!reader.isEOF()) {
			Pair<DimensionMap, String> result = reader.readRowToDimensionMapString();
			if (result != null) {
				arffWriter.writeData(result.p1, Double.parseDouble(result.p2));
			} else {
				Logs.log("Went past end of file!?!", this);
			}
		}

		// Close and save the data.
		reader.close();
		String arffPath = arffWriter.getPath();
		arffWriter.close();
		outputCSV = FileWriter.makeFileObject("temp", null, csvPath);
		outputARFF = FileWriter.makeFileObject("temp", null, arffPath);
	}

	public LabelRegion<Integer> getMajorSubRegion(LabelRegion<Integer> region, Img<UnsignedByteType> mask) {
		LabelRegions<Integer> subRegions = utils.getSubRegions(region, mask, connectedness.equals("4 Connected"));
		long maxSize = 1; // This is hopefully to avoid quantifying subregions
							// that are only a pixel in size.
		LabelRegion<Integer> majorSubRegion = null;
		RandomAccess<BoolType> ra = Regions.iterable(region).randomAccess();
		long[] pos = new long[region.numDimensions()];
		Cursor<Void> c = null;
		for (LabelRegion<Integer> subRegion : subRegions) {
			c = Regions.iterable(subRegion).cursor();
			c.fwd(); // This needs to be called to get the cursor into the first
						// pixel of the region.
			c.localize(pos); // localize the pos variable to this location
			ra.setPosition(pos); // set the position of the random accessible to
									// pos
			if (subRegion.size() > maxSize && ra.get().get()) {
				majorSubRegion = subRegion;
				maxSize = subRegion.size();
			}
		}
		return majorSubRegion;
	}

	@SuppressWarnings("unchecked")
	public boolean putZernike(DimensionMap mapM, int id, LabelRegion<Integer> reg) {
		if (this.isCanceled()) {
			this.close();
			return false;
		}
		if (zernike) {
			if (this.opZernike == null) {
				opZernike = IJ2PluginUtility.ij().op().op(ZernikeFeatureSet.class, reg, zernikeMomentMin,
						zernikeMomentMax);
			}

			PositionableIterableRegion<BoolType> temp = reg;
			IterableRegion<BoolType> temp2 = temp;
			IterableInterval<Void> temp3 = temp2;
			IterableInterval<BitType> convert = Converters.convert(temp3, new MyConverter(), new BitType());

			Map<NamedFeature, DoubleType> results = opZernike.compute1(convert);
			for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
				DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName());
				newMap.put("Id", "" + id);
				newMap.put("Label", "" + idToLabelMap.get(id));
				this.write(newMap, result.getValue().get());
			}
		}
		return true;
	}

	// @SuppressWarnings("unchecked")
	// public boolean putTamura(DimensionMap mapM, int id, LabelRegion<Integer>
	// reg, Img<T> image)
	// {
	// if(this.isCanceled())
	// {
	// this.close();
	// return false;
	// }
	// if(tamura)
	// {
	// if(opTamura == null)
	// {
	// opTamura = IJ2PluginUtility.ij().op().op(Tamura2DFeatureSet.class,
	// image);
	// }
	// Map<NamedFeature, DoubleType> results =
	// opTamura.compute(Views.Regions.sample(reg, image));
	// for(Entry<NamedFeature, DoubleType> result : results.entrySet())
	// {
	// DimensionMap newMap = mapM.copyAndSet("Measurement=" +
	// result.getKey().getName());
	// newMap.put("Id", "" + id);
	// newMap.put("Label", "" + idToLabelMap.get(id));
	// this.write(newMap, result.getValue().get());
	// }
	// }
	// return true;
	// }

	@SuppressWarnings("unchecked")
	public boolean putGeometric(DimensionMap mapM, int id, LabelRegion<Integer> reg) {
		if (this.isCanceled()) {
			this.close();
			return false;
		}
		if (geometric) {
			
			Polygon toCalc = utils.convert(reg);
			if (toCalc == null) {
				Logs.log("Encountered a null polygon for geometric2d features. id:" + id + ", label:" + reg.getLabel(),
						FeatureExtraction.class);
			}
			if (opGeometric == null) {
				opGeometric = IJ2PluginUtility.ij().op().op(Geometric2DFeatureSet.class, toCalc);
			}

			Map<NamedFeature, DoubleType> results = opGeometric.compute1(toCalc);
			// Map<NamedFeature, DoubleType> results =
			// opGeometric.compute1(reg);
			for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
				DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName());
				newMap.put("Id", "" + id);
				newMap.put("Label", "" + idToLabelMap.get(id));
				this.write(newMap, result.getValue().getRealDouble());
			}
			DimensionMap newMap = mapM
					.copyAndSet("Measurement=" + net.imagej.ops.Ops.Geometric.Size.class.getName() + "Iterable");
			newMap.put("Id", "" + id);
			newMap.put("Label", "" + idToLabelMap.get(id));
			this.write(newMap, (double) reg.size());
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	public boolean putStats(DimensionMap mapM, int id, LabelRegion<Integer> reg, Img<T> image) {
		if (this.isCanceled()) {
			this.close();
			return false;
		}
		if (stats) {
			if (opStats == null) {
				opStats = IJ2PluginUtility.ij().op().op(StatsFeatureSet.class, (IterableInterval<T>) image);
			}
			Map<NamedFeature, DoubleType> results = opStats.compute1(Regions.sample(reg, image));
			for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
				DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName());
				newMap.put("Id", "" + id);
				newMap.put("Label", "" + idToLabelMap.get(id));
				this.write(newMap, result.getValue().get());
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	public boolean putHaralick2D(DimensionMap mapM, int id, LabelRegion<Integer> reg, Img<T> image) {
		Map<NamedFeature, DoubleType> results;

		if (haralick2D) {
			if (opHaralick2DHor == null || opHaralick2DVer == null) {
				opHaralick2DHor = IJ2PluginUtility.ij().op().op(Haralick2DFeatureSet.class, image, DoubleType.class,
						(int) haralickGrayLevels, (int) haralickDistance, MatrixOrientation2D.HORIZONTAL);
				opHaralick2DVer = IJ2PluginUtility.ij().op().op(Haralick2DFeatureSet.class, image,
						(int) haralickGrayLevels, (int) haralickDistance, MatrixOrientation2D.VERTICAL);
				if (haralickNumDirections.equals("4")) {
					if (opHaralick2DDiag == null || opHaralick2DAntiDiag == null) {
						opHaralick2DDiag = IJ2PluginUtility.ij().op().op(Haralick2DFeatureSet.class, image,
								(int) haralickGrayLevels, (int) haralickDistance, MatrixOrientation2D.DIAGONAL);
						opHaralick2DAntiDiag = IJ2PluginUtility.ij().op().op(Haralick2DFeatureSet.class, image,
								(int) haralickGrayLevels, (int) haralickDistance, MatrixOrientation2D.ANTIDIAGONAL);
					}
				}
			}

			results = (Map<NamedFeature, DoubleType>) opHaralick2DHor.compute1(Regions.sample(reg,  image));

			///// Horizontal /////
			if (this.isCanceled()) {
				this.close();
				return false;
			}
			for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
				DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName() + "_Horizontal");
				newMap.put("Id", "" + id);
				newMap.put("Label", "" + idToLabelMap.get(id));
				this.write(newMap, result.getValue().getRealDouble());
			}

			///// Vertical /////
			if (this.isCanceled()) {
				this.close();
				return false;
			}
			results = opHaralick2DVer.compute1(Regions.sample(reg, image));
			for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
				DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName() + "_Vertical");
				newMap.put("Id", "" + id);
				newMap.put("Label", "" + idToLabelMap.get(id));
				this.write(newMap, result.getValue().getRealDouble());
			}

			if (haralickNumDirections.equals("4")) {
				///// Diagonal /////
				if (this.isCanceled()) {
					this.close();
					return false;
				}
				results = opHaralick2DDiag.compute1(Regions.sample(reg, image));
				for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
					DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName() + "_Diagonal");
					newMap.put("Id", "" + id);
					newMap.put("Label", "" + idToLabelMap.get(id));
					this.write(newMap, result.getValue().getRealDouble());
				}

				///// Antidiagonal /////
				if (this.isCanceled()) {
					this.close();
					return false;
				}
				results = opHaralick2DAntiDiag.compute1(Regions.sample(reg, image));
				for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
					DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName() + "_AntiDiagonal");
					newMap.put("Id", "" + id);
					newMap.put("Label", "" + idToLabelMap.get(id));
					this.write(newMap, result.getValue().getRealDouble());
				}
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	public boolean putHistogram(DimensionMap mapM, int id, LabelRegion<Integer> reg, Img<T> image) {
		if (this.isCanceled()) {
			this.close();
			return false;
		}
		if (histogram) {
			if (opHistogram == null) {
				opHistogram = IJ2PluginUtility.ij().op().op(HistogramFeatureSet.class, image, histogramBins);
			}
			IterableInterval<T> itr = Regions.sample(reg, image);
			Map<NamedFeature, LongType> ret = opHistogram.compute1(itr);
			for (Entry<NamedFeature, LongType> result : ret.entrySet()) {
				DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName());
				newMap.put("Id", "" + id);
				newMap.put("Label", "" + idToLabelMap.get(id));
				this.write(newMap, result.getValue().getRealDouble());
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	public void putMoments(DimensionMap mapM, int id, LabelRegion<Integer> reg, Img<T> image) {
		if (moments) {
			if (opMoments == null) {
				opMoments = IJ2PluginUtility.ij().op().op(ImageMomentsFeatureSet.class, (IterableInterval<T>) image);
			}
			Map<NamedFeature, DoubleType> results = opMoments.compute1(Regions.sample(reg, image));
			for (Entry<NamedFeature, DoubleType> result : results.entrySet()) {
				DimensionMap newMap = mapM.copyAndSet("Measurement=" + result.getKey().getName());
				newMap.put("Id", "" + id);
				newMap.put("Label", "" + idToLabelMap.get(id));
				this.write(newMap, result.getValue().get());
			}
		}
	}

	// public static boolean contains(LabelRegion<?> region, Point p)
	// {
	// LabelRegionCursor c = region.localizingCursor();
	// do
	// {
	// if(c.getIntPosition(0) == p.x && c.getIntPosition(1) == p.y)
	// {
	// return true;
	// }
	// c.next();
	// }
	// while(c.hasNext());
	//
	// return false;
	// }

	// public TreeMap<String, Object> getPixelValues(Wand wand, IdPoint p,
	// ByteProcessor impMask, FloatProcessor impImage1, FloatProcessor
	// impImage2)
	// {
	// Vector<Double> m1 = null;
	// PointList pts = null;
	// if(impMask.getPixel(p.x, p.y) == 255) // if we land on a cell that made
	// // it through thresholding
	// {
	// wand.autoOutline(p.x, p.y); // outline it
	// if(wand.npoints > 0)
	// {
	// // The roi helps for using getLength() (DON'T USE Roi.TRACED_ROI., IT
	// SCREWS UP THE Polygon OBJECTS!!!! Bug emailed to ImageJ folks
	// Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints,
	// Roi.POLYGON);
	//
	// // The polygon helps for using contains()
	// java.awt.Polygon poly = new java.awt.Polygon(wand.xpoints, wand.ypoints,
	// wand.npoints);
	// Rectangle r = roi.getBounds();
	// m1 = new Vector<Double>();
	// pts = new PointList();
	// for(int i = r.x; i < r.x + r.width; i++)
	// {
	// for(int j = r.y; j < r.y + r.height; j++)
	// {
	// // innerBoundary
	// if(poly.contains(i, j) && impMask.getPixelValue(i, j) == 255)
	// {
	// m1.add((double) impImage1.getPixelValue(i, j));
	// pts.add(i, j);
	// // Logs.log("In - " + innerT, this);
	// }
	// }
	// }
	// }
	// }
	// TreeMap<String, Object> ret = new TreeMap<String, Object>();
	// ret.put("m1", m1);
	// ret.put("xy", pts);
	// return ret;
	// }

	class MyConverter implements Converter<Void, BitType> {

		@Override
		public void convert(Void input, BitType output) {
			output.set(true);
		}
	}

	public void quantifyIntensityFeatures(DimensionMap map, int id, LabelRegion<Integer> region,
			Img<T> intensityImage) {
		// return false if canceled, which means
		if (region == null || region.size() <= 1 || intensityImage == null) {
			return;
		}

		this.putStats(map, id, region, intensityImage);
		this.putHaralick2D(map, id, region, intensityImage);
		this.putHistogram(map, id, region, intensityImage);
		this.putMoments(map, id, region, intensityImage);
	}

	public void quantifyGeometricFeatures(DimensionMap mapM, int id, LabelRegion<Integer> region) {
		if (region == null || region.size() <= 1) {
			return;
		}

		this.putGeometric(mapM, id, region);
		this.putZernike(mapM, id, region);
	}

}
