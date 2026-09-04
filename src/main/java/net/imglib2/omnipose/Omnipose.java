package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class Omnipose
{

	/**
	 * Run Omnipose with the given parameters on the given image, and return the
	 * resulting label image, and optionally the flows.
	 *
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param <R>
	 *            the pixel type of the output label image. It can be either
	 *            UnsignedShortType or UnsignedIntType (if the number of labels
	 *            in one image is larger than 65k).
	 * @param img
	 *            the input image. X and Y axes must be at positions 0 and 1
	 *            respectively. If not, a {@link IllegalArgumentException} is
	 *            thrown.
	 * @param axisInfo
	 *            the AxisInfo of the input image.
	 * @param outputType
	 *            the desired pixel type for the output labels image. It can be
	 *            either UnsignedShortType or UnsignedIntType (if the number of
	 *            labels in one image is larger than 65k).
	 * @param params
	 *            the parameters to run Omnipose with.
	 * @param listener
	 *            the listener to receive progress updates and messages during
	 *            the execution of the Omnipose task.
	 *
	 * @return a {@link OmniposeOutput} object containing the label image, and
	 *         optionally the flows image.
	 *
	 * @throws BuildException
	 *             if installing and building the Python environment fails.
	 * @throws IOException
	 *             if reading the Python scripts or environment specifications
	 *             fails.
	 * @throws InterruptedException
	 *             if the Python process is interrupted while running.
	 * @throws TaskException
	 *             if executing the Python script fails.
	 */
	public static < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > OmniposeOutput< R > omnipose(
			final RandomAccessibleInterval< T > img,
			final AxisInfo axisInfo,
			final R outputType,
			final OmniposeParameters params,
			final ApposeTaskListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		return run( img, axisInfo, outputType, params, listener );
	}

	public static < T extends RealType< T > & NativeType< T > > OmniposeOutput< UnsignedShortType > omnipose(
			final RandomAccessibleInterval< T > img,
			final AxisInfo axisInfo,
			final OmniposeParameters params,
			final ApposeTaskListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		return omnipose( img, axisInfo, new UnsignedShortType(), params, listener );
	}

	/**
	 * Core method to run Omnipose. To be used by other methods in this class.
	 *
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param img
	 *            the input image. X and Y axes must be at positions 0 and 1
	 *            respectively.
	 * @param axisInfo
	 *            the AxisInfo of the input image.
	 * @param params
	 *            the parameters to run Omnipose with.
	 * @return an {@link OmniposeOutput} containing the label image, and
	 *         optionally the flows image. If flows are not computed, the output
	 *         will contain only the label image.
	 */
	private static < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > OmniposeOutput< R > run(
			final RandomAccessibleInterval< T > input,
			final AxisInfo axisInfo,
			final R outputType,
			final OmniposeParameters params,
			final ApposeTaskListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		if ( axisInfo.X() != 0 || axisInfo.Y() != 1 )
			throw new IllegalArgumentException( "X and Y axes must be at positions 0 and 1 respectively." );

		// Do we have a 5D image? If yes we process time-point by time-point.
		final long nt = axisInfo.nTimePoints( input );
		final long nz = axisInfo.nZ( input );

		final OmniposeRunner< T, R > runner;
		if ( nt > 1 && nz > 1 )
		{
			// Drop time.
			final AxisInfo axisInfoNoT = axisInfo.removeTimeDim();
			final Dimensions dimsNotT = Views.hyperSlice( input, axisInfo.T(), 0 );
			runner = OmniposeRunner.create( params, dimsNotT, axisInfoNoT, input.getType(), outputType, listener );
		}
		else
		{
			runner = OmniposeRunner.create( params, input, axisInfo, input.getType(), outputType, listener );
		}

		try (runner)
		{
			runner.init();

			if ( nt > 1 && nz > 1 )
			{
				// Placeholder for full labels output: XYZT.
				final long[] inputDims = input.dimensionsAsLongArray();
				final long[] ldims = new long[] {
						inputDims[ axisInfo.X() ],
						inputDims[ axisInfo.Y() ],
						inputDims[ axisInfo.Z() ],
						inputDims[ axisInfo.T() ] };
				final Dimensions labelsDim = FinalDimensions.wrap( ldims );
				final Img< R > outputLabels = Util.getArrayOrCellImgFactory( labelsDim, outputType ).create( ldims );

				// Placeholder for flows output if needed.
				final Img< UnsignedByteType > outputFlows;
				if ( params.computeFlows )
				{
					// XYCZT, with nC = 3 for the 3 flows.
					final long[] fdims = new long[] {
							ldims[ 0 ],
							ldims[ 1 ],
							3,
							ldims[ 2 ],
							ldims[ 3 ] };
					// 3 channels in the flows output
					outputFlows = Util.getArrayOrCellImgFactory( labelsDim, new UnsignedByteType() ).create( fdims );
				}
				else
				{
					outputFlows = null;
				}

				/*
				 * Process time-point by time-point.
				 */

				for ( int t = 0; t < nt; t++ )
				{
					// Input reslice.
					runner.setInput( Views.hyperSlice( input, axisInfo.T(), t ) );

					// Execute
					runner.run();

					// Labels output reslice.
					runner.getOutputLabels( Views.hyperSlice( outputLabels, 3, t ) );

					// Flows output reslice.
					if ( params.computeFlows )
						runner.getOutputFlows( Views.hyperSlice( outputFlows, 4, t ) );
				}

				// Return all time-points.
				@SuppressWarnings( { "rawtypes", "unchecked" } )
				final OmniposeOutput< R > out = new OmniposeOutput(
						outputLabels,
						axisInfo.removeChannelDim(),
						outputFlows,
						( axisInfo.C() < 0 ) ? axisInfo.insertChannelDim( 2 ) : axisInfo );
				return out;
			}
			else
			{
				// Otherwise process in one go.
				runner.setInput( input );
				runner.run();
				return runner.getOutput();
			}
		}
	}

	private Omnipose()
	{}
}
