package function.plugin.plugins.featureExtraction;

import net.imagej.ops.FunctionOp;
import net.imagej.ops.Ops;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.roi.geometric.Polygon;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import function.plugin.IJ2.IJ2PluginUtility;
import function.plugin.plugins.featureExtraction.ConnectedComponents.StructuringElement;

/**
 * Had to copy this class out temporarily while SNAPSHOTS conflict and this code is in flux.
 * Taken from imglib2-algorithm ConnectedComponents
 * 
 * @author Tobias Pietzsch
 *
 */
public class FeatureUtils {
	
	private static FunctionOp<Object, Object> contourFunc;
	
	public static <I extends IntegerType< I >> ImgLabeling<Integer, IntType> getConnectedComponents(RandomAccessibleInterval<I> inputImg, boolean fourConnected)
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
	
	@SuppressWarnings({ "unchecked", "rawtypes", "unused" })
	public static <T> Polygon convert(final LabelRegion<T> src) {
		if (contourFunc == null) {
			contourFunc = (FunctionOp) IJ2PluginUtility.ij().op().function(Ops.Geometric.Contour.class, Polygon.class, src, true,
				true);
		}
		// FIXME: can we make this faster?
		final Polygon p = (Polygon) contourFunc.compute(src);
		return p;
	}
	
//	public static <I extends IntegerType< I >> ImgLabeling<Integer, IntType> getConnectedComponents(RandomAccessibleInterval<UnsignedByteType> reg, Img< I > inputImg, boolean fourConnected)
//	{
//		StructuringElement se = null;
//		if(fourConnected)
//		{
//			se = StructuringElement.FOUR_CONNECTED;
//		}
//		else
//		{
//			se = StructuringElement.EIGHT_CONNECTED;
//		}
//		
//		long[] dimensions = new long[reg.numDimensions()];
//		reg.dimensions(dimensions);
//		final Img< IntType > indexImg = ArrayImgs.ints( dimensions );
//		ImgLabeling<Integer, IntType> labeling = new ImgLabeling<Integer, IntType>(Regions.sample(reg, inputImg));
//		ConnectedComponents.labelAllConnectedComponents(inputImg, labeling, new LabelGenerator(), se);
//		
//		return labeling;
//	}
	
	public static <I extends IntegerType< I >> Img< UnsignedShortType > getConnectedComponentsImage(Img< I > inputImg, boolean fourConnected)
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

