package net.imglib2.omnipose;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

/**
 * Represents the output of Omnipose. Stores masks and flows possibly.
 *
 * @param <T>
 *            the type of the labels output. Can be {@link UnsignedShortType} or
 *            {@link UnsignedIntType} if N labels > 65k.
 */
public class OmniposeOutput< T extends IntegerType< T > & NativeType< T > >
{

	/**
	 * The labels output from Omnipose. Can be {@link UnsignedShortType} or
	 * {@link UnsignedIntType}.
	 */
	public final RandomAccessibleInterval< T > labels;

	/**
	 * The flows output from Omnipose. Always 3 channels. Can be null if the
	 * flows were not returned by Omnipose.
	 */
	public final RandomAccessibleInterval< UnsignedByteType > flows;

	/**
	 * The axes of the labels output.
	 */
	public final AxisInfo axesLabels;

	/**
	 * The axes of the flows output. Can be null if the flows were not returned
	 * by Omnipose.
	 */
	public final AxisInfo axesFlows;

	public OmniposeOutput( final RandomAccessibleInterval< T > labels, final AxisInfo axesLabels )
	{
		this( labels, axesLabels, null, null );
	}

	public OmniposeOutput( final RandomAccessibleInterval< T > labels, final AxisInfo axesLabels, final RandomAccessibleInterval< UnsignedByteType > flows, final AxisInfo axesFlows )
	{
		this.labels = labels;
		this.axesLabels = axesLabels;
		this.flows = flows;
		this.axesFlows = axesFlows;
	}
}
