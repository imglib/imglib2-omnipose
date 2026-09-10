package net.imglib2.omnipose;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.apposed.appose.TaskException;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.util.AbstractShmApposeRunner;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.appose.util.PixiApposeTaskRunner;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;

/**
 * Runs Omnipose through Appose using managed shared-memory image buffers.
 * <p>
 * This class does not manage {@code ShmImg} instances directly. Image buffers
 * are owned by the composed shared-memory image store in
 * {@link AbstractShmApposeRunner}.
 */
public class OmniposeRunner extends AbstractShmApposeRunner
{

	private static final String INPUT = "input";

	private static final String LABELS = "output_labels";

	private static final String FLOWS = "output_flows";

	private AxisInfo axisInfo;

	private Dimensions inputDimensions;

	private Dimensions labelDimensions;

	private Dimensions flowDimensions;

	private NativeType< ? > labelType;

	private OmniposeRunner( final String envName, final ApposeTaskListener listener )
	{
		super( new PixiApposeTaskRunner(
				OmniposeRunner.class.getResource( "/pixi.toml" ),
				OmniposeRunner.class.getResource( "/omnipose_utils.py" ),
				OmniposeRunner.class.getResource( "/omnipose.py" ),
				envName,
				listener ) );
	}

	public < T extends RealType< T > & NativeType< T > > void setInput( final RandomAccessibleInterval< T > input, final AxisInfo axisInfo )
	{
		setInput( input, axisInfo, new UnsignedShortType() );
	}

	public < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > void setInput( final RandomAccessibleInterval< T > input, final AxisInfo axisInfo, final R outputType )
	{
		Objects.requireNonNull( input, "input" );
		Objects.requireNonNull( axisInfo, "axisInfo" );
		Objects.requireNonNull( outputType, "outputType" );

		this.axisInfo = axisInfo;
		this.inputDimensions = new FinalDimensions( input );
		this.labelDimensions = outputLabelDimensions( input, axisInfo );
		this.labelType = outputType.copy();

		writeImage( INPUT, input );
		allocateImage( LABELS, outputType, labelDimensions );
	}

	public void run( final OmniposeParameters params ) throws InterruptedException, TaskException
	{
		Objects.requireNonNull( params, "params" );

		if ( !hasImage( INPUT ) )
			throw new IllegalStateException( "The input image has not been set. Please execute setInput() first." );

		if ( params.computeFlows )
		{
			flowDimensions = outputFlowDimensions( inputDimensions, axisInfo );
			allocateImage( FLOWS, new UnsignedByteType(), flowDimensions );
		}
		else
		{
			removeImage( FLOWS );
			flowDimensions = null;
		}

		// Everything Omnipose but images.
		final Map< String, Object > parameters = params.toApposeMap( axisInfo );
		// The input / output images.
		final Map< String, Object > map = apposeMap( parameters );
		if ( !params.computeFlows )
			map.put( FLOWS, null );

		runTask( map );
	}

	public < R extends IntegerType< R > & NativeType< R > > void getOutputLabels( final RandomAccessibleInterval< R > outputLabels )
	{
		readImage( LABELS, outputLabels );
	}

	public < R extends IntegerType< R > & NativeType< R > > Img< R > getOutputLabels()
	{
		requireProcessed();
		@SuppressWarnings( "unchecked" )
		final R type = ( R ) labelType.copy();
		final Img< R > outputLabels = Util
				.getArrayOrCellImgFactory( labelDimensions, type )
				.create( labelDimensions );

		getOutputLabels( outputLabels );
		return outputLabels;
	}

	public void getOutputFlows( final RandomAccessibleInterval< UnsignedByteType > outputFlows )
	{
		requireProcessed();
		if ( !hasImage( FLOWS ) )
			throw new IllegalStateException( "Output flows were not computed." );

		readImage( FLOWS, outputFlows );
	}

	public Img< UnsignedByteType > getOutputFlows()
	{
		requireProcessed();
		if ( !hasImage( FLOWS ) )
			throw new IllegalStateException( "Output flows were not computed." );

		final Img< UnsignedByteType > outputFlows = Util
				.getArrayOrCellImgFactory( flowDimensions, new UnsignedByteType() )
				.create( flowDimensions );

		getOutputFlows( outputFlows );
		return outputFlows;
	}

	public < R extends IntegerType< R > & NativeType< R > > OmniposeOutput< R > getOutput()
	{
		final Img< R > outputLabels = getOutputLabels();
		final Img< UnsignedByteType > outputFlows = hasImage( FLOWS )
				? getOutputFlows()
				: null;

		final AxisInfo axesLabels = axisInfo.removeChannelDim();
		final AxisInfo axesFlows = outputFlows == null
				? null
				: axesLabels.insertChannelDim( 2 );

		return new OmniposeOutput<>(
				outputLabels,
				axesLabels,
				outputFlows,
				axesFlows );
	}

	public static OmniposeRunner create( final ApposeTaskListener listener, final String torchVersion )
	{
		final String envName = "omnipose-" + getTorchInstallSuffix( torchVersion );
		return new OmniposeRunner( envName, listener );
	}

	private static Dimensions outputLabelDimensions( final Dimensions input, final AxisInfo axisInfo )
	{
		final long[] dims = input.dimensionsAsLongArray();

		if ( axisInfo.C() < 0 )
			return new FinalDimensions( dims );

		final long[] result = new long[ dims.length - 1 ];

		int j = 0;
		for ( int d = 0; d < dims.length; d++ )
			if ( d != axisInfo.C() )
				result[ j++ ] = dims[ d ];

		return new FinalDimensions( result );
	}

	private static Dimensions outputFlowDimensions( final Dimensions input, final AxisInfo axisInfo )
	{
		final long[] dims = input.dimensionsAsLongArray();

		if ( axisInfo.C() < 0 )
		{
			final long[] result = new long[ dims.length + 1 ];

			result[ 0 ] = dims[ 0 ];
			result[ 1 ] = dims[ 1 ];
			result[ 2 ] = 3;

			for ( int d = 2; d < dims.length; d++ )
				result[ d + 1 ] = dims[ d ];

			return new FinalDimensions( result );
		}

		final long[] result = dims.clone();
		result[ axisInfo.C() ] = 3;

		return new FinalDimensions( result );
	}

	static String getTorchInstallSuffix( final String torchVersion )
	{
		if ( getOperatingSystem() == OperatingSystem.MACOS )
			return "cpu";

		if ( !hasCUDA() )
			return "cpu";

		return torchVersion;
	}

	public enum OperatingSystem
	{
		WINDOWS, LINUX, MACOS, UNKNOWN
	}

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

	private static boolean hasCUDA()
	{
		if ( getOperatingSystem() == OperatingSystem.MACOS )
			return false;

		try
		{
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
