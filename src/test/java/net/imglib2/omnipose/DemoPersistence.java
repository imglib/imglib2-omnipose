package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * This demo showcase how the runner is optimized to keep the Python environment
 * alive and the Omnipose model deployed accross run.
 * <p>
 * If the model specified across runs is the same, it is not re-deployed in the
 * Python script, saving time. When the model changes, the Python script detects
 * it and triggers its deployment.
 */
public class DemoPersistence
{

	public static < T extends RealType< T > & NativeType< T > > void main( final String[] args ) throws IOException, BuildException, InterruptedException, TaskException
	{
		final String imagePath1 = "samples/20230331_washed_XY1.ome-1_stabilized_cropped-t61-crop1.tif";
		final ImagePlus imp1 = IJ.openImage( imagePath1 );
		final Img< T > img1 = ImageJFunctions.wrap( imp1 );

		final String imagePath2 = "samples/20230331_washed_XY1.ome-1_stabilized_cropped-t61-crop2.tif";
		final ImagePlus imp2 = IJ.openImage( imagePath2 );
		final Img< T > img2 = ImageJFunctions.wrap( imp2 );

		final AxisInfo inputAxes = AxisInfo.XY;
		final OmniposeParameters params1 = OmniposeParameters.builder()
				.model( OmniposeBuiltinModels.BACT_PHASE_AFFINITY )
				.channels( 1, 0 )
				.computeFlows( true )
				.torchVersion( "cpu" )
				.build();

		try (
				final OmniposeRunner runner = OmniposeRunner.create(
						ApposeTaskListener.STD,
						params1.torchVersion );)
		{
			System.out.println( "Init." );
			runner.init();
			System.out.println( "Init done." );
			
			System.out.println();
			System.out.println( "------------------------------------" );
			System.out.println( "Run 1 with model " + params1.buitInModel );
			System.out.println( "------------------------------------" );
			runner.setInput( img1, inputAxes );
			runner.run( params1 );
			System.out.println( "Done." );

			System.out.println();
			System.out.println( "------------------------------------" );
			System.out.println( "Run 2 with the same model" );
			System.out.println( "------------------------------------" );
			runner.setInput( img1, inputAxes );
			runner.run( params1 );

			final OmniposeParameters params2 = OmniposeParameters.builder()
					.model( OmniposeBuiltinModels.BACT_PHASE_OMNI )
					.channels( 1, 0 )
					.computeFlows( true )
					.torchVersion( "cpu" )
					.build();

			System.out.println();
			System.out.println( "------------------------------------" );
			System.out.println( "Run 3 with another model" + params2.buitInModel );
			System.out.println( "------------------------------------" );
			runner.setInput( img1, inputAxes );
			runner.run( params2 );

			System.out.println();
			final UnsignedLongType outputType = new UnsignedLongType();
			System.out.println( "------------------------------------" );
			System.out.println( "Run 4: Same everything but the output type: " + outputType.getClass().getSimpleName() );
			System.out.println( "------------------------------------" );
			runner.setInput( img1, inputAxes, outputType );
			runner.run( params2 );
			// Can I read it?
			final Img< UnsignedLongType > output1 = ArrayImgs.unsignedLongs( img1.dimensionsAsLongArray() );
			runner.getOutputLabels( output1 );

			System.out.println();
			System.out.println( "------------------------------------" );
			System.out.println( "Run 5: Change the input image" );
			System.out.println( "------------------------------------" );
			runner.setInput( img2, inputAxes, outputType );
			runner.run( params2 );
			// Can I read it?
			final Img< UnsignedLongType > output2 = ArrayImgs.unsignedLongs( img2.dimensionsAsLongArray() );
			runner.getOutputLabels( output2 );

			System.out.println();
			final FloatType inputType = new FloatType();
			System.out.println( "------------------------------------" );
			System.out.println( "Run 6: Change the type of the input image: " + inputType.getClass().getSimpleName() );
			System.out.println( "------------------------------------" );
			final Img< FloatType > img3 = ArrayImgs.floats( img2.dimensionsAsLongArray() );
			LoopBuilder.setImages( img2, img3 )
					.multiThreaded()
					.forEachPixel( ( i, o ) -> o.set( i.getRealFloat() ) );
			runner.setInput( img3, inputAxes, outputType );
			runner.run( params2 );
			// Reuse the previous output image to read the new output.
			runner.getOutputLabels( output2 );
		}
	}
}
