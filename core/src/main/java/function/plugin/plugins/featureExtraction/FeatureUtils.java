package function.plugin.plugins.featureExtraction;

import java.util.Iterator;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import function.plugin.plugins.featureExtraction.ConnectedComponents.StructuringElement;

/**
 * Had to copy this class out temporarily while SNAPSHOTS conflict and this code is in flux.
 * Taken from imglib2-algorithm ConnectedComponents
 * 
 * @author Tobias Pietzsch
 *
 */
public class FeatureUtils {
	
	public static <T extends IntegerType< T >> ImgLabeling<Integer, IntType> getConnectedComponents(Img< T > inputImg, boolean fourConnected)
	{
		StructuringElement se = null;
		if(fourConnected)
		{
			se = StructuringElement.FOUR_CONNECTED;
		}
		else
		{
			se = StructuringElement.EIGHT_CONNECTED;
		}
		
		long[] dimensions = new long[inputImg.numDimensions()];
		inputImg.dimensions(dimensions);
		final Img< IntType > indexImg = ArrayImgs.ints( dimensions );
		ImgLabeling<Integer, IntType> labeling = new ImgLabeling<Integer, IntType>(indexImg);
		ConnectedComponents.labelAllConnectedComponents(inputImg, labeling, new LabelGenerator(), se);
		
		return labeling;
	}
	
	public static <T extends IntegerType< T >> Img< UnsignedShortType > getConnectedComponentsImage(Img< T > inputImg, boolean fourConnected)
	{
		StructuringElement se = null;
		if(fourConnected)
		{
			se = ConnectedComponents.StructuringElement.FOUR_CONNECTED;
		}
		else
		{
			se = ConnectedComponents.StructuringElement.EIGHT_CONNECTED;
		}
		
		long[] dimensions = new long[inputImg.numDimensions()];
		inputImg.dimensions(dimensions);
		final Img< UnsignedShortType > indexImg = ArrayImgs.unsignedShorts( dimensions );
		//ImgLabeling<Integer, UnsignedShortType> labeling = new ImgLabeling<Integer, UnsignedShortType>(indexImg);
		ConnectedComponents.labelAllConnectedComponents(inputImg, indexImg, se);
		
		return indexImg;
	}
}

class LabelGenerator implements Iterator<Integer>
{
	private int current = -1;

	@Override
	public boolean hasNext()
	{
		if(current < Integer.MAX_VALUE-1)
		{
			return true;
		}
		return false;
	}
	
	@Override
	public Integer next()
	{
		current = current + 1;
		return current;
	}
	
	@Override
	public void remove()
	{
		// Do nothing
	}
}
