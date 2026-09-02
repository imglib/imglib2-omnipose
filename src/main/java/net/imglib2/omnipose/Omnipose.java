package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.ShmImg;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.ImgUtil;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
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
		final String envName = "omnipose-" + getTorchInstallSuffix( params.torchVersion );
		final String pythonScriptPath = "/omnipose.py";
		final String pythonInitScriptPath = "/omnipose_init.py";
		return run( img, axisInfo, outputType, params, pythonInitScriptPath, pythonScriptPath, envName, listener );
	}

	public static < T extends RealType< T > & NativeType< T > > OmniposeOutput< UnsignedShortType > omnipose(
			final RandomAccessibleInterval< T > img,
			final AxisInfo axisInfo,
			final OmniposeParameters params,
			final ApposeTaskListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		return omnipose( img, axisInfo, new UnsignedShortType(), params, listener );
	}

	public static < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > OmniposeRunner< T, R > omniposeRunner(
			final OmniposeParameters params,
			final ApposeTaskListener listener,
			final ShmImg< T > input,
			final AxisInfo inputAxisInfo,
			final ShmImg< R > outputLabels,
			final ShmImg< UnsignedByteType > outputFlows ) throws BuildException, IOException, InterruptedException, TaskException
	{
		final String envName = "omnipose-" + getTorchInstallSuffix( params.torchVersion );
		final String pythonScriptPath = "/omnipose.py";
		final String pythonInitScriptPath = "/omnipose_init.py";

		return new OmniposeRunner<>(
				params,
				pythonInitScriptPath,
				pythonScriptPath,
				envName,
				listener,
				input,
				inputAxisInfo,
				outputLabels,
				outputFlows );
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
	 * @param pythonScriptPath
	 *            the path to the Python script to run.
	 * @param envName
	 *            the name of the Python environment to create and use.
	 * @return a list containing the label image, and optionally the flows
	 *         image. If flows are not computed, the list will contain only the
	 *         label image.
	 */
	private static < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > OmniposeOutput< R > run(
			final RandomAccessibleInterval< T > input,
			final AxisInfo axisInfo,
			final R outputType,
			final OmniposeParameters params,
			final String pythonInitScriptPath,
			final String pythonScriptPath,
			final String envName,
			final ApposeTaskListener listener ) throws BuildException, IOException, InterruptedException, TaskException
	{
		if ( axisInfo.X() != 0 || axisInfo.Y() != 1 )
			throw new IllegalArgumentException( "X and Y axes must be at positions 0 and 1 respectively." );

		// Placeholders declaration.
		final ShmImg< T > inputShm;
		final AxisInfo inputAxisInfo;
		final ShmImg< R > outputLabelsShm;
		final ShmImg< UnsignedByteType > outputFlowsShm;

		// Do we have a 5D image? If yes we process timepoint by timepoint.
		final long nt = axisInfo.nTimePoints( input );
		final long nz = axisInfo.nZ( input );

		if ( nt > 1 && nz > 1 )
		{
			// Temp image won't have time dim.
			inputAxisInfo = axisInfo.removeTimeDim();
			// We create placeholders for a single timepoint.
			final IntervalView< T > singleTP = Views.hyperSlice( input, axisInfo.T(), 0 );
			inputShm = createInputShmImg( singleTP );
			outputLabelsShm = createOutputLabelsShmImg( singleTP, axisInfo.removeTimeDim(), outputType );
			if ( params.computeFlows )
				outputFlowsShm = createOutputFlowsShmImg( singleTP, axisInfo.removeTimeDim() );
			else
				outputFlowsShm = null;
		}
		else
		{
			inputAxisInfo = axisInfo;
			// We create placeholders for the whole image.
			inputShm = createInputShmImg( input );
			outputLabelsShm = createOutputLabelsShmImg( input, axisInfo, outputType );
			if ( params.computeFlows )
				outputFlowsShm = createOutputFlowsShmImg( input, axisInfo );
			else
				outputFlowsShm = null;
		}

		// Create the runner, configured on the ShmImg.
		try (final OmniposeRunner< T, R > runner = new OmniposeRunner<>(
				params,
				pythonInitScriptPath,
				pythonScriptPath,
				envName,
				listener,
				inputShm,
				inputAxisInfo,
				outputLabelsShm,
				outputFlowsShm ))
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
				 * Process time point by time point.
				 */

				for ( int t = 0; t < nt; t++ )
				{
					// Input reslice.
					final RandomAccessibleInterval< T > inputTp = Views.hyperSlice( input, axisInfo.T(), t );

					// Labels output reslice.
					final RandomAccessibleInterval< R > outputLabelsTp = Views.hyperSlice( outputLabels, 3, t );

					// Flows output reslice.
					final RandomAccessibleInterval< UnsignedByteType > outputFlowsTp;
					if ( params.computeFlows )
						outputFlowsTp = Views.hyperSlice( outputFlows, 4, t );
					else
						outputFlowsTp = null;

					// Write input slice into the shared memory placeholder.
					ImgUtil.copy( inputTp, inputShm );

					// Exec and write output in the right place.
					runner.run();

					// Write output in the resliced output images.
					ImgUtil.copy( outputLabelsShm, outputLabelsTp );
					if ( params.computeFlows )
						ImgUtil.copy( outputFlowsShm, outputFlowsTp );
				}

				// Close placeholder ShmImgs.
				inputShm.close();
				outputLabelsShm.close();
				if ( params.computeFlows )
					outputFlowsShm.close();

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
				// Write input in the shared memory placeholder.
				ImgUtil.copy( input, inputShm );
				runner.run();

				// And return with the shared image we created.
				final AxisInfo axesLabels = axisInfo.removeChannelDim();
				final AxisInfo axesFlows = axesLabels.insertChannelDim( 2 );
				return new OmniposeOutput< R >( outputLabelsShm, axesLabels, outputFlowsShm, axesFlows );
			}
		}
	}

	/**
	 * Creates an empty shared memory image with the same dimensions and pixel
	 * type as the input.
	 *
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param input
	 *            the input image.
	 * @return a new ShmImg.
	 */
	public static < T extends RealType< T > & NativeType< T > > ShmImg< T > createInputShmImg( final RandomAccessibleInterval< T > input )
	{
		return createInputShmImg( input, input.getType().createVariable() );
	}

	/**
	 * Creates an empty shared memory image with the specified dimensions and
	 * pixel type.
	 *
	 * @param <T>
	 *            the pixel type.
	 * @param input
	 *            the dimensions.
	 * @param type
	 *            the pixel type.
	 * @return a new ShmImg.
	 */
	public static < T extends RealType< T > & NativeType< T > > ShmImg< T > createInputShmImg( final Dimensions input, final T type )
	{
		final long[] dims = input.dimensionsAsLongArray();
		final int[] dims2 = new int[ dims.length ];
		for ( int i = 0; i < dims.length; i++ )
			dims2[ i ] = ( int ) dims[ i ];
		return new ShmImg<>( type, dims2 );
	}

	/**
	 * Creates a shared memory image suitable to hold Omnipose flows output,
	 * with the right dimensions for the specified image input.
	 *
	 * @param input
	 *            the input image.
	 * @param axisInfo
	 *            the AxisInfo of the input image.
	 * @return a new ShmImg.
	 */
	public static ShmImg< UnsignedByteType > createOutputFlowsShmImg( final Dimensions input, final AxisInfo axisInfo )
	{
		final long[] dims = input.dimensionsAsLongArray();
		if ( axisInfo.C() < 0 )
		{
			final int[] dims2 = new int[ dims.length + 1 ];
			dims2[ 0 ] = ( int ) dims[ 0 ];
			dims2[ 1 ] = ( int ) dims[ 1 ];
			dims2[ 2 ] = 3; // 3 channels for the flows.
			for ( int i = 2; i < dims.length; i++ )
				dims2[ i + 1 ] = ( int ) dims[ i ];
			return new ShmImg<>( new UnsignedByteType(), dims2 );
		}
		final int[] dims2 = new int[ dims.length ];
		for ( int i = 0; i < dims.length; i++ )
		{
			if ( i == axisInfo.C() )
				dims2[ i ] = 3; // 3 channels for the flows.
			else
				dims2[ i ] = ( int ) dims[ i ];
		}
		return new ShmImg<>( new UnsignedByteType(), dims2 );
	}

	/**
	 * Creates a shared memory image suitable to hold Omnipose labels output,
	 * with the right dimensions for the specified image input.
	 *
	 * @param <R>
	 *            the pixel type of the output label image.
	 * @param input
	 *            the input image.
	 * @param axisInfo
	 *            the AxisInfo of the input image.
	 * @param outputType
	 *            the desired pixel type for the output labels image. It can be
	 *            either UnsignedShortType or UnsignedIntType (if the number of
	 *            labels in one image is larger than 65k).
	 * @return a new ShmImg.
	 */
	public static < R extends IntegerType< R > & NativeType< R > > ShmImg< R > createOutputLabelsShmImg( final Dimensions input, final AxisInfo axisInfo, final R outputType )
	{
		final long[] dims = input.dimensionsAsLongArray();
		if ( axisInfo.C() < 0 )
		{
			final int[] dims2 = new int[ dims.length ];
			for ( int i = 0; i < dims.length; i++ )
				dims2[ i ] = ( int ) dims[ i ];
			return new ShmImg< R >( outputType, dims2 );
		}
		// We drop the channel dim.
		final int[] dims2 = new int[ dims.length - 1 ];
		int j = 0;
		for ( int i = 0; i < dims.length; i++ )
		{
			if ( i != axisInfo.C() )
			{
				dims2[ j ] = ( int ) dims[ i ];
				j++;
			}
		}
		return new ShmImg< R >( outputType, dims2 );
	}

	/**
	 * Filters and returns the suffix to use for installing the correct version
	 * of PyTorch.
	 * <p>
	 * This method checks the operating system and CUDA availability to
	 * determine the appropriate suffix for installing PyTorch. If you are on a
	 * Mac or do not have CUDA available, it returns "cpu". Otherwise, it
	 * returns the specified torchVersion.
	 *
	 * @param torchVersion
	 *            the version of PyTorch to install if CUDA is available.
	 * @return the suffix to use for installing the correct version of PyTorch.
	 */
	private static String getTorchInstallSuffix( final String torchVersion )
	{
		// if MacOS, return "-cpu"
		if ( getOperatingSystem() == OperatingSystem.MACOS )
			return "cpu";

		if ( !hasCUDA() )
			return "cpu";

		return torchVersion;
	}

	/** Enum representing the main operating systems. */
	public enum OperatingSystem
	{
		WINDOWS, LINUX, MACOS, UNKNOWN
	}

	/**
	 * Returns the current operating system.
	 *
	 * @return the current operating system.
	 */
	private static OperatingSystem getOperatingSystem()
	{
		final String os = System.getProperty( "os.name" ).toLowerCase();
		if ( os.contains( "mac" ) || os.contains( "darwin" ) )
			return OperatingSystem.MACOS;
		if ( os.contains( "win" ) )
			return OperatingSystem.WINDOWS;
		if ( os.contains( "nux" ) || os.contains( "nix" ) || os.contains( "aix" ) )
			return OperatingSystem.LINUX;
		return OperatingSystem.UNKNOWN;
	}

	/**
	 * Checks if CUDA is available on the system by trying to execute
	 * {@code nvidia-smi}. This method returns {@code false} on macOS, as CUDA
	 * is not supported on that platform.
	 *
	 * @return {@code true} if CUDA is available, {@code false} otherwise.
	 */
	private static Boolean hasCUDA()
	{
		if ( getOperatingSystem() == OperatingSystem.MACOS )
			return false;
		try
		{
			// try to run nvidia-smi to check if it is available
			final ProcessBuilder pb = new ProcessBuilder( "nvidia-smi" );
			pb.redirectErrorStream( true );
			final Process process = pb.start();
			process.waitFor();
			return process.exitValue() == 0;
		}
		catch ( final IOException | InterruptedException e )
		{
			return false;
		}
	}

	private Omnipose()
	{}
}
