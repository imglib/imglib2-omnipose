package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

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

		try (OmniposeRunner runner = OmniposeRunner.create( listener, params.torchVersion ))
		{
			runner.init();
			if ( nt > 1 && nz > 1 )
			{
				final OmniposeRunnerWrapper wrapper = new OmniposeRunnerWrapper( runner, d -> {} );
				return wrapper.run( input, axisInfo, outputType, params );
			}
			else
			{
				// Otherwise process in one go.
				runner.setInput( input, axisInfo, outputType );
				runner.run( params );
				return runner.getOutput();
			}
		}
	}

	private Omnipose()
	{}
}
