package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.Dimensions;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

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

		final ImagePlus imp = IJ.openImage( imagePath1 );
		final Img< T > img1 = ImageJFunctions.wrap( imp );

		final AxisInfo inputAxes = AxisInfo.XY;
		final OmniposeParameters params1 = OmniposeParameters.builder()
				.model( OmniposeBuiltinModels.BACT_PHASE_AFFINITY )
				.channels( 1, 0 )
				.computeFlows( true )
				.torchVersion( "cpu" )
				.build();

		final Dimensions dims1 = img1;
		try (
				final OmniposeRunner2< T, UnsignedShortType > runner = OmniposeRunner2.create(
						dims1,
						inputAxes,
						img1.getType(),
						ApposeTaskListener.STD,
						params1.torchVersion );)
		{
			System.out.println( "Init." );
			runner.init( params1 );
			System.out.println( "Init done." );
			
			System.out.println();
			System.out.println( "Run 1 with model " + params1.buitInModel );
			runner.setInput( img1 );
			runner.run( params1 );
			System.out.println( "Done." );

			System.out.println();
			System.out.println( "Run 2 with the same model" );
			runner.setInput( img1 );
			runner.run( params1 );

			final OmniposeParameters params2 = OmniposeParameters.builder()
					.model( OmniposeBuiltinModels.BACT_PHASE_OMNI )
					.channels( 1, 0 )
					.computeFlows( true )
					.torchVersion( "cpu" )
					.build();

			System.out.println();
			System.out.println( "Run 3 with another model" + params2.buitInModel );
			runner.setInput( img1 );
			runner.run( params2 );
		}
	}
}
