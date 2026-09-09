package net.imglib2.omnipose;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class BasicUsage
{

	public static void main( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		try
		{
//			basicUsage( args );
			outputType( args );
//			omniposeRunner( args );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}

	public static < T extends RealType< T > & NativeType< T > > void outputType( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final ImagePlus imp = IJ.openImage( "samples/20230331_washed_XY1.ome-1_stabilized_cropped-t61.tif" );
		final Img< T > img = ImageJFunctions.wrap( imp );

		final RandomAccessibleInterval< T > input = img;
		final AxisInfo inputAxes = AxisInfo.XY;
		final ApposeTaskListener listener = ApposeTaskListener.STD;
		final OmniposeParameters params = OmniposeParameters.builder()
				.model( OmniposeBuiltinModels.BACT_PHASE_AFFINITY )
				.channels( 1, 0 )
				.computeFlows( true )
				.torchVersion( "cpu" )
				.build();

		final OmniposeOutput< UnsignedIntType > output = Omnipose.omnipose(
				input,
				inputAxes,
				new UnsignedIntType(),
				params,
				listener );

		@SuppressWarnings( "unused" )
		final RandomAccessibleInterval< UnsignedIntType > labels = output.labels;
		@SuppressWarnings( "unused" )
		final RandomAccessibleInterval< UnsignedByteType > flows = output.flows;

		ImageJFunctions.show( output.labels ).setTitle( "Omnipose output" );
		ImageJFunctions.show( output.flows ).setTitle( "Omnipose flows" );
	}

	public static < T extends RealType< T > & NativeType< T > > void basicUsage( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		// Demo preparation. We use IJ for this one.
		ImageJ.main( args );
		final ImagePlus imp = IJ.openImage( "samples/20230331_washed_XY1.ome-1_stabilized_cropped-t61.tif" );
		imp.show();
		final Img< T > img = ImageJFunctions.wrap( imp );

		// Input
		final RandomAccessibleInterval< T > input = img;
		// You need to specify the dimensionality of your input
		final AxisInfo inputAxes = AxisInfo.XY;

		// Get messages about installing and processing
		final ApposeTaskListener listener = ApposeTaskListener.STD;

		// Specify the parameters
		final OmniposeParameters params = OmniposeParameters.builder()
				.model( OmniposeBuiltinModels.BACT_PHASE_AFFINITY )
				.channels( 1, 0 )
				.diameter( 17. )
				.computeFlows( true )
				.torchVersion( "cpu" )
				.build();

		final OmniposeOutput< UnsignedShortType > output = Omnipose.omnipose( input, inputAxes, params, listener );

		final RandomAccessibleInterval< UnsignedShortType > labels = output.labels;
		final RandomAccessibleInterval< UnsignedByteType > flows = output.flows;

		ImageJFunctions.show( labels ).setTitle( "Omnipose output" );
		ImageJFunctions.show( flows ).setTitle( "Omnipose flows" );
	}

	public static void omniposeRunner( final String[] args ) throws BuildException, IOException, InterruptedException, TaskException
	{
		// Create fake images. Replace by your own images here.
		// The constraint is that the images need to have the same dimensions
		// and axes.
		final int nImages = 10;
		final int width = 512;
		final int height = width;
		final AxisInfo axes = AxisInfo.XY;

		final List< RandomAccessibleInterval< UnsignedShortType > > outputImages = new ArrayList<>( nImages );
		final List< RandomAccessibleInterval< UnsignedByteType > > inputImages = new ArrayList<>( nImages );
		for ( int i = 0; i < nImages; i++ )
		{
			final RandomAccessibleInterval< UnsignedByteType > img = ArrayImgs.unsignedBytes( width, height );
			inputImages.add( img );
		}

		// Specify the parameters for Omnipose. Adjust to your needs.
		final OmniposeParameters params = OmniposeParameters.builder()
				.model( OmniposeBuiltinModels.CYTO2 )
				.channels( 1, 0 )
				.computeFlows( true )
				.build();

		// Time everything.
		long startTime = System.currentTimeMillis();

		// Now we create the Omnipose runner and use it in a
		// try-with-resources block. This way we are sure that the shared tmp
		// images are and the Omnipose runner are properly closed and cleaned up
		// after use.

		final OmniposeRunner2 runner = OmniposeRunner2.create( ApposeTaskListener.VOID, params.torchVersion );

		try (runner)
		{
			// Initialize the runner. This will deploy the Python environment
			// and script if not already done, and prepare everything for
			// running Omnipose.
			startTime = System.currentTimeMillis();
			runner.init();
			System.out.println( String.format( "Runner initialization time: %.2f seconds", ( System.currentTimeMillis() - startTime ) / 1000. ) );

			// Run Omnipose on each image.
			for ( int i = 0; i < nImages; i++ )
			{
				System.out.println( String.format( "\nProcessing image %d/%d", i + 1, nImages ) );
				final RandomAccessibleInterval< UnsignedByteType > input = inputImages.get( i );

				// Copy the input image to the tmp location.
				startTime = System.currentTimeMillis();
				runner.setInput( input, axes );
				System.out.println( String.format( "Input copy time: %.2f seconds", ( System.currentTimeMillis() - startTime ) / 1000. ) );

				// Run Omnipose. The results will be written in the tmpLabels
				// and tmpFlows images.
				startTime = System.currentTimeMillis();
				runner.run( params );
				System.out.println( String.format( "Omnipose run time: %.2f seconds", ( System.currentTimeMillis() - startTime ) / 1000. ) );

				// Copy the output to a new image.
				startTime = System.currentTimeMillis();
				final RandomAccessibleInterval< UnsignedShortType > outputLabels = ArrayImgs.unsignedShorts( input.dimensionsAsLongArray() );
				runner.getOutputLabels( outputLabels );
				System.out.println( String.format( "Output copy to a new image time: %.2f seconds", ( System.currentTimeMillis() - startTime ) / 1000. ) );
				outputImages.add( outputLabels );
			}

			startTime = System.currentTimeMillis();
		}
		System.out.println( String.format( "Closing shared resources time: %.2f seconds", ( System.currentTimeMillis() - startTime ) / 1000. ) );

		// At this point the runner and the tmp shared memory images are
		// closed and cleaned up, and you can safely exit or do other things
		// with the output images.
	}

}
