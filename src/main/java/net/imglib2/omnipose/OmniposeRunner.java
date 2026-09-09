package net.imglib2.omnipose;

import java.io.IOException;
import java.util.Map;

import org.apposed.appose.TaskException;

import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.ShmImg;
import net.imglib2.appose.util.AbstractPixiRunner2;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.ImgUtil;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;

/**
 * Specialized class that runs Omnipose. This class exists so that we can write
 * results in a pre-allocated output data structure.
 */
public class OmniposeRunner extends AbstractPixiRunner2
{

	private ShmImg< ? > inputShm;

	private ShmImg< ? > outputLabelsShm;

	private ShmImg< UnsignedByteType > outputFlowsShm;

	private AxisInfo axisInfo;

	private boolean processed = false;

	private boolean needsRegen;

	private OmniposeRunner(
			final String envName,
			final ApposeTaskListener listener )
	{
		super(
				OmniposeRunner.class.getResource( "/pixi.toml" ),
				OmniposeRunner.class.getResource( "/omnipose_utils.py" ),
				OmniposeRunner.class.getResource( "/omnipose.py" ),
				envName,
				listener );
	}

	public < T extends RealType< T > & NativeType< T > > void setInput(
			final RandomAccessibleInterval< T > input,
			final AxisInfo axisInfo )
	{
		setInput( input, axisInfo, new UnsignedShortType() );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > void setInput(
			final RandomAccessibleInterval< T > input,
			final AxisInfo axisInfo,
			final R outputType )
	{
		processed = false;
		boolean regenInputHolder = false;

		// Un-initialized?
		if ( inputShm == null )
			regenInputHolder = true;
		// Dimensions changed?
		else if ( !Intervals.equalDimensions( ( Dimensions ) input, ( Dimensions ) inputShm ) )
			regenInputHolder = true;
		// Type changed?
		else if ( !inputShm.firstElement().getClass().equals( input.randomAccess().get().getClass() ) )
			regenInputHolder = true;

		if ( regenInputHolder )
		{
			if ( inputShm != null )
				inputShm.close();
			if ( outputLabelsShm != null )
				outputLabelsShm.close();
			inputShm = createInputShmImg( input, input.randomAccess().get() );
			outputLabelsShm = createOutputLabelsShmImg( input, axisInfo, outputType );
			// For the flows output, we wait to know if we are asked to compute
			// them.
		}
		// No need to regen the input, but what if the ouput type changed?
		else if ( !outputLabelsShm.firstElement().getClass().equals( outputType.getClass() ) )
		{
			if ( outputLabelsShm != null )
				outputLabelsShm.close();
			outputLabelsShm = createOutputLabelsShmImg( input, axisInfo, outputType );
		}

		this.needsRegen = regenInputHolder;
		this.axisInfo = axisInfo;
		ImgUtil.copy( ( Img ) input, ( Img ) inputShm );
	}

	public void run( final OmniposeParameters params ) throws InterruptedException, TaskException
	{
		if ( inputShm == null )
			throw new IllegalStateException( "The input image has not been set. Please execute setInput() first." );

		// Shall we prepare the output flows shm?
		if ( params.computeFlows )
		{
			if ( outputFlowsShm == null || needsRegen )
			{
				if ( outputFlowsShm != null )
					outputFlowsShm.close();
				outputFlowsShm = createOutputFlowsShmImg( inputShm, axisInfo );
			}
		}
		else
		{
			if ( outputFlowsShm != null )
			{
				outputFlowsShm.close();
				outputFlowsShm = null;
			}
		}

		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final Map< String, Object > map = params.toApposeMap(
				( ShmImg ) inputShm,
				axisInfo,
				( ShmImg ) outputLabelsShm,
				outputFlowsShm );
		super.run( map );
		processed = true;
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public < R extends NativeType< R > & IntegerType< R > > void getOutputLabels( final RandomAccessibleInterval< R > outputLabels )
	{
		if ( !processed )
			throw new IllegalStateException( "The input image has been set but the task has not been run yet. Please execute run() first." );

		// Check that the outputLabels has the same dimensions as the buffer
		if ( !Intervals.equalDimensions( ( Dimensions ) outputLabels, ( Dimensions ) outputLabelsShm ) )
			throw new IllegalArgumentException( "The specified outputLabels image has different dimensions ("
					+ Intervals.toString( ( Dimensions ) outputLabels )
					+ ") than the output buffer image ("
					+ Intervals.toString( ( Dimensions ) outputLabelsShm ) );

		// Check that the outputLabels has the same type as the buffer
		if ( !outputLabelsShm.firstElement().getClass().equals( outputLabels.randomAccess().get().getClass() ) )
			throw new IllegalArgumentException( "The specified output labels image has a different type ("
					+ outputLabels.randomAccess().get().getClass().getSimpleName()
					+ ") than the output buffer image ("
					+ outputLabelsShm.firstElement().getClass().getSimpleName() );

		ImgUtil.copy( ( ShmImg ) outputLabelsShm, outputLabels );
	}

	public < R extends NativeType< R > & IntegerType< R > > Img< R > getOutputLabels()
	{
		@SuppressWarnings( "unchecked" )
		final Img< R > outputLabels = ( Img< R > ) Util.getArrayOrCellImgFactory( outputLabelsShm, outputLabelsShm.getType() ).create( outputLabelsShm );
		getOutputLabels( outputLabels );
		return outputLabels;
	}

	public void getOutputFlows( final RandomAccessibleInterval< UnsignedByteType > outputFlows )
	{
		if ( !processed )
			throw new IllegalStateException( "The input image has been set but the task has not been run yet. Please execute run() first." );
		if ( outputFlowsShm == null )
			throw new IllegalStateException( "Output flows were not computed." );

		// Check that the outputFlows has the same dimensions as the buffer
		if ( !Intervals.equalDimensions( ( Dimensions ) outputFlows, ( Dimensions ) outputFlowsShm ) )
			throw new IllegalArgumentException( "The specified output flows image has different dimensions ("
					+ Intervals.toString( ( Dimensions ) outputFlows )
					+ ") than the output flow buffer image ("
					+ Intervals.toString( ( Dimensions ) outputFlowsShm ) );

		ImgUtil.copy( outputFlowsShm, outputFlows );
	}

	public Img< UnsignedByteType > getOutputFlows()
	{
		if ( outputFlowsShm == null )
			throw new IllegalStateException( "Output flows were not computed." );
		final Img< UnsignedByteType > outputFlows = Util.getArrayOrCellImgFactory( outputFlowsShm, outputFlowsShm.getType() ).create( outputFlowsShm );
		getOutputFlows( outputFlows );
		return outputFlows;
	}

	public < R extends NativeType< R > & IntegerType< R > > OmniposeOutput< R > getOutput()
	{
		final Img< R > outputLabels = getOutputLabels();
		final Img< UnsignedByteType > outputFlows = outputFlowsShm != null ? getOutputFlows() : null;
		final AxisInfo axesLabels = axisInfo.removeChannelDim();
		final AxisInfo axesFlows = axesLabels.insertChannelDim( 2 );
		return new OmniposeOutput<>( outputLabels, axesLabels, outputFlows, axesFlows );
	}

	@Override
	public void close()
	{
		super.close();
		if ( inputShm != null )
			inputShm.close();
		if ( outputLabelsShm != null )
			outputLabelsShm.close();
		if ( outputFlowsShm != null )
			outputFlowsShm.close();
	}

	public static OmniposeRunner create( final ApposeTaskListener listener, final String torchVersion )
	{
		final String envName = "omnipose-" + getTorchInstallSuffix( torchVersion );
		return new OmniposeRunner( envName, listener );
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
	private static < T extends RealType< T > & NativeType< T > > ShmImg< T > createInputShmImg( final Dimensions input, final T type )
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
	private static ShmImg< UnsignedByteType > createOutputFlowsShmImg( final Dimensions input, final AxisInfo axisInfo )
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
	private static < R extends NativeType< R > > ShmImg< R > createOutputLabelsShmImg( final Dimensions input, final AxisInfo axisInfo, final R outputType )
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
	static String getTorchInstallSuffix( final String torchVersion )
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
}
