package function.plugin.plugins.imageProcessing;

import java.io.File;
import java.util.List;
import java.util.TreeMap;

import org.scijava.plugin.Plugin;

import Database.DBObjects.JEXData;
import Database.DBObjects.JEXEntry;
import Database.DataReader.ImageReader;
import Database.DataWriter.ImageWriter;
import Database.SingleUserDatabase.JEXWriter;
import function.plugin.mechanism.InputMarker;
import function.plugin.mechanism.JEXPlugin;
import function.plugin.mechanism.MarkerConstants;
import function.plugin.mechanism.OutputMarker;
import function.plugin.mechanism.ParameterMarker;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import jex.statics.JEXDialog;
import jex.statics.JEXStatics;
import jex.utilities.FunctionUtility;
import miscellaneous.CSVList;
import miscellaneous.StringUtility;
import tables.Dim;
import tables.DimensionMap;

/**
 * This is a JEXperiment function template To use it follow the following instructions
 * 
 * 1. Fill in all the required methods according to their specific instructions 2. Place the file in the Functions/SingleDataPointFunctions folder 3. Compile and run JEX!
 * 
 * JEX enables the use of several data object types The specific API for these can be found in the main JEXperiment folder. These API provide methods to retrieve data from these objects, create new objects and handle the data they contain.
 * 
 */

@Plugin(
		type = JEXPlugin.class,
		name="Adjust Image Intensities (Multi-Channel)",
		menuPath="Image Processing",
		visible=true,
		description="Adjust defined intensities in the original image to be new defined intensities, scaling all other intensities accordingly."
		)
public class AdjustImageMultiChannel extends JEXPlugin {

	public AdjustImageMultiChannel()
	{}
	
	/////////// Define Inputs ///////////
	
	@InputMarker(uiOrder=1, name="Image", type=MarkerConstants.TYPE_IMAGE, description="Image to be adjusted.", optional=false)
	JEXData imageData;
	
	/////////// Define Parameters ///////////
	
	@ParameterMarker(uiOrder=1, name="Channel Dim Name", description="Name of the 'Channel' dimension for this image object.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="Channel")
	String channelDimName;
	
	@ParameterMarker(uiOrder=1, name="Old Min", description="Current 'min' to be mapped to new min value. Comma separated list of image intensity values or one value to apply to all channels.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0.0")
	String oldMins;
	
	@ParameterMarker(uiOrder=2, name="Old Max", description="Current 'max' to be mapped to new max value. Comma separated list of image intensity values or one value to apply to all channels.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="4095.0")
	String oldMaxs;
	
	@ParameterMarker(uiOrder=3, name="New Min", description="New value for current 'min'. Comma separated list of image intensity values or one value to apply to all channels.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="0.0")
	String newMins;
	
	@ParameterMarker(uiOrder=4, name="New Max", description="New value for current 'max'. Comma separated list of image intensity values or one value to apply to all channels.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="65535.0")
	String newMaxs;
	
	@ParameterMarker(uiOrder=5, name="Gamma", description="0.1-5.0, value of 1 results in no change. Comma separated list of values or one value to apply to all channels.", ui=MarkerConstants.UI_TEXTFIELD, defaultText="1.0")
	String gammas;
	
	@ParameterMarker(uiOrder=6, name="Output Bit Depth", description="Depth of the outputted image for all channels.", ui=MarkerConstants.UI_DROPDOWN, choices={ "8", "16", "32" }, defaultChoice=1)
	int bitDepth;
	
	/////////// Define Outputs ///////////
	
	@OutputMarker(uiOrder=1, name="Adjusted Image", type=MarkerConstants.TYPE_IMAGE, flavor="", description="The resultant adjusted image", enabled=true)
	JEXData output;
	
	@Override
	public int getMaxThreads()
	{
		return 10;
	}

	@Override
	public boolean run(JEXEntry entry)
	{
		// Validate the input data
		if(imageData == null || !imageData.getTypeName().getType().equals(JEXData.IMAGE))
		{
			return false;
		}
		
		if(oldMins == null || oldMins.equals("") || oldMaxs == null || oldMaxs.equals("") || newMins == null || newMins.equals("") || newMaxs == null || newMaxs.equals(""))
		{
			JEXDialog.messageDialog("All intensity values must be specified. None can be left blank. Aborting.");
			return false;
		}
		
		if(imageData.getDimTable().getDimWithName(channelDimName) == null)
		{
			JEXDialog.messageDialog("A dimension with the name: " + channelDimName + "' does not exist in '" + imageData.getTypeName().getName() + "' in Entry: X" + entry.getTrayX() + " Y" + entry.getTrayY() + ". Aborting.");
			return false;
		}
		
		
		
		// Gather the parameters.
		Dim channelDim = imageData.getDimTable().getDimWithName(channelDimName);
		CSVList oldMinsList = getCSVList(oldMins, channelDim);
		CSVList oldMaxsList = getCSVList(oldMaxs, channelDim);
		CSVList newMinsList = getCSVList(newMins, channelDim);
		CSVList newMaxsList = getCSVList(newMaxs, channelDim);
		CSVList gammasList = getCSVList(gammas, channelDim);
		
		if(channelDim.size() != oldMinsList.size() || channelDim.size() != oldMaxsList.size() || channelDim.size() != newMinsList.size() || channelDim.size() != newMaxsList.size() || channelDim.size() != gammasList.size())
		{
			JEXDialog.messageDialog("The number of intensity values listed for each of the mins and maxs must be the same size as the number of channels in the image. Aborting.");
			return false;
		}
		
		try
		{
			TreeMap<String,Double> oldMinsMap = getMap(oldMinsList, channelDim);
			TreeMap<String,Double> oldMaxsMap = getMap(oldMaxsList, channelDim);
			TreeMap<String,Double> newMinsMap = getMap(newMinsList, channelDim);
			TreeMap<String,Double> newMaxsMap = getMap(newMaxsList, channelDim);
			TreeMap<String,Double> gammasMap = getMap(gammasList, channelDim);
			
			// Run the function
			TreeMap<DimensionMap,String> imageMap = ImageReader.readObjectToImagePathTable(imageData);
			TreeMap<DimensionMap,String> outputImageMap = new TreeMap<DimensionMap,String>();
			int count = 0, percentage = 0;
			String tempPath;
			for (DimensionMap map : imageMap.keySet())
			{
				// Call helper method
				tempPath = saveAdjustedImage(imageMap.get(map), oldMinsMap.get(map.get(channelDimName)), oldMaxsMap.get(map.get(channelDimName)), newMinsMap.get(map.get(channelDimName)), newMaxsMap.get(map.get(channelDimName)), gammasMap.get(map.get(channelDimName)), bitDepth);
				if(tempPath != null)
				{
					outputImageMap.put(map, tempPath);
				}
				count = count + 1;
				percentage = (int) (100 * ((double) (count) / ((double) imageMap.size())));
				JEXStatics.statusBar.setProgressPercentage(percentage);
			}
			if(outputImageMap.size() == 0)
			{
				return false;
			}
			
			this.output = ImageWriter.makeImageStackFromPaths("temp",outputImageMap);
			
			// Return status
			return true;
			
		}
		catch(NumberFormatException e)
		{
			JEXDialog.messageDialog("Couldn't parse one of the number values provided.");
			e.printStackTrace();
			return false;
		}		
	}
	
	public static String saveAdjustedImage(String imagePath, double oldMin, double oldMax, double newMin, double newMax, double gamma, int bitDepth)
	{
		// Get image data
		File f = new File(imagePath);
		if(!f.exists())
		{
			return null;
		}
		ImagePlus im = new ImagePlus(imagePath);
		FloatProcessor imp = (FloatProcessor) im.getProcessor().convertToFloat(); // should be a float processor
		
		// Adjust the image
		FunctionUtility.imAdjust(imp, oldMin, oldMax, newMin, newMax, gamma);
		
		// Save the results
		ImagePlus toSave = FunctionUtility.makeImageToSave(imp, "false", bitDepth);
		String imPath = JEXWriter.saveImage(toSave);
		im.flush();
		
		// return the filepath
		return imPath;
	}
	
	private CSVList getCSVList(String param, Dim channelDim)
	{
		CSVList temp = new CSVList(param);
		CSVList ret = new CSVList();
		if(temp.size() == 1)
		{
			for(int i = 0; i < channelDim.values().size(); i++)
			{
				// Repeat the value for as many channels that exist in the channel dimension.
				ret.add(StringUtility.removeWhiteSpaceOnEnds(temp.get(0)));
			}
		}
		else
		{
			for(String p : temp)
			{
				// This list may not be the same length as the channel dim but we'll test for that elsewhere.
				ret.add(StringUtility.removeWhiteSpaceOnEnds(p));
			}
		}
		
		return ret;
	}
	
	private TreeMap<String,Double> getMap(List<String> values, Dim channelDim) throws NumberFormatException
	{
		TreeMap<String,Double> ret = new TreeMap<>();
		for(String channel : channelDim.values())
		{
			ret.put(channel, Double.parseDouble(values.get(channelDim.index(channel))));
		}
		return ret;
	}
}