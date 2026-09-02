package net.imglib2.omnipose;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imglib2.appose.ShmImg;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class OmniposeParameters
{

	public final OmniposeBuiltinModels buitInModel;

	public final String customModel;

	// Core parameters

	public final List< Integer > channels;

	public final double diameter;

	// Thresholds

	public final double flowThreshold;

	public final double cellProbThreshold;

	// Normalization & pre-processing

	public final boolean normalize;

	public final boolean resample;

	// 3D processing

	public final boolean do3D;

	public final double anisotropy;

	public final double stitchThreshold;

	public final int flow3dSmooth;

	// Advanced parameters

	public final boolean useGpu;

	public final double minSize;

	// Advanced processing

	public final double tileOverlap;

	public final boolean computeFlows;

	public final int nIter;

	public final String torchVersion;

	private OmniposeParameters(
			final OmniposeBuiltinModels buitInModel,
			final List< Integer > channels,
			final String customModel,
			final double diameter,
			final boolean do3D,
			final boolean normalize,
			final double flowThreshold,
			final double cellProbThreshold,
			final boolean useGpu,
			final double minSize,
			final double anisotropy,
			final double stitchThreshold,
			final boolean resample,
			final double tileOverlap,
			final boolean computeFlows,
			final int flow3dSmooth,
			final int nIter,
			final String torchVersion )
	{
		this.buitInModel = buitInModel;
		this.channels = channels;
		this.customModel = customModel;
		this.diameter = diameter;
		this.do3D = do3D;
		this.normalize = normalize;
		this.flowThreshold = flowThreshold;
		this.cellProbThreshold = cellProbThreshold;
		this.useGpu = useGpu;
		this.minSize = minSize;
		this.anisotropy = anisotropy;
		this.stitchThreshold = stitchThreshold;
		this.resample = resample;
		this.tileOverlap = tileOverlap;
		this.computeFlows = computeFlows;
		this.flow3dSmooth = flow3dSmooth;
		this.nIter = nIter;
		this.torchVersion = torchVersion;
	}

	/**
	 * Creates a parameters map suitable for passing to Appose, using the
	 * specified image as input, and the parameter values stored in this object.
	 *
	 * @param <T>
	 *            the pixel type of the input image.
	 * @param input
	 *            the input image.
	 * @return a new map.
	 */
	public < T extends RealType< T > & NativeType< T >, R extends IntegerType< R > & NativeType< R > > Map< String, Object > toApposeMap(
			final ShmImg< T > input,
			final AxisInfo axisInfo,
			final ShmImg< R > outputLabels,
			final ShmImg< UnsignedByteType > outputFlows )
	{
		final Map< String, Object > inputs = new HashMap<>();

		final boolean isBuiltInModel = customModel == null || customModel.equals( "" );
		inputs.put( "model_name", isBuiltInModel ? buitInModel.modelName() : null );
		// return null if custom model
		inputs.put( "custom_model", isBuiltInModel ? null : customModel );

		inputs.put( "chan1", channels.get( 0 ) );
		inputs.put( "chan2", channels.get( 1 ) );

		// Inputs
		inputs.put( "input", input.ndArray() );
		final AxisInfo axisInfoPython = axisInfo.toPython();
		inputs.put( "t_axis", axisInfoPython.T() < 0 ? null : axisInfoPython.T() );
		inputs.put( "z_axis", axisInfoPython.Z() < 0 ? null : axisInfoPython.Z() );
		inputs.put( "channel_axis", axisInfoPython.C() < 0 ? null : axisInfoPython.C() );

		// Outputs
		inputs.put( "output_labels", outputLabels.ndArray() );
		inputs.put( "output_flows", outputFlows == null ? null : outputFlows.ndArray() );

		// Other params.
		inputs.put( "use_3D", do3D );
		inputs.put( "diameter", diameter );
		inputs.put( "stitch_threshold", stitchThreshold );
		inputs.put( "anisotropy", anisotropy );
		inputs.put( "compute_flows", computeFlows );
		inputs.put( "resample", resample );
		inputs.put( "normalize", normalize );
		inputs.put( "flow_threshold", flowThreshold );
		inputs.put( "cellprob_threshold", cellProbThreshold );
		inputs.put( "min_size", minSize );
		inputs.put( "tile_overlap", tileOverlap );
		inputs.put( "flow3D_smooth", flow3dSmooth );
		inputs.put( "niter", nIter <= 0 ? null : nIter );
		inputs.put( "use_gpu", useGpu );

		return inputs;
	}

	public static class Builder
	{

		private OmniposeBuiltinModels model = OmniposeBuiltinModels.BACT_PHASE_AFFINITY;

		private String customModel = null;

		// Core parameters

		private double diameter = 5.0;

		private List< Integer > channels = List.of( 0, 0 );;

		// Thresholds

		private double flowThreshold = 0.4;

		private double cellProbThreshold = 0.0;

		// Normalization & pre-processing

		private boolean normalize = true;

		private boolean resample = true;

		// 3D processing

		private boolean do3D = false;

		private double anisotropy = 1.0;

		private double stitchThreshold = 0.0;

		private int flow3dSmooth = 0;

		// Advanced parameters

		private boolean useGpu = true;

		private double minSize = 15.0;

		// Advanced processing

		private double tileOverlap = 0.1;

		private boolean computeFlows = false;

		private int nIter = 200;

		private String torchVersion = "cpu";

		public Builder customModel( final String customModel )
		{
			this.customModel = customModel;
			return this;
		}

		public Builder channels( final int channel1, final int channel2 )
		{
			this.channels = Arrays.asList( channel1, channel2 );
			return this;
		}

		public Builder diameter( final double diameter )
		{
			this.diameter = diameter;
			return this;
		}

		public Builder do3D( final boolean do3D )
		{
			this.do3D = do3D;
			return this;
		}

		public Builder normalize( final boolean normalize )
		{
			this.normalize = normalize;
			return this;
		}

		public Builder flowThreshold( final double flowThreshold )
		{
			this.flowThreshold = flowThreshold;
			return this;
		}

		public Builder cellProbThreshold( final double cellProbThreshold )
		{
			this.cellProbThreshold = cellProbThreshold;
			return this;
		}

		public Builder useGpu( final boolean useGpu )
		{
			this.useGpu = useGpu;
			return this;
		}

		public Builder minSize( final double minSize )
		{
			this.minSize = minSize;
			return this;
		}

		public Builder anisotropy( final double anisotropy )
		{
			this.anisotropy = anisotropy;
			return this;
		}

		public Builder stitchThreshold( final double stitchThreshold )
		{
			this.stitchThreshold = stitchThreshold;
			return this;
		}

		public Builder resample( final boolean resample )
		{
			this.resample = resample;
			return this;
		}

		public Builder tileOverlap( final double tileOverlap )
		{
			this.tileOverlap = tileOverlap;
			return this;
		}

		public Builder computeFlows( final boolean computeFlows )
		{
			this.computeFlows = computeFlows;
			return this;
		}

		public Builder flow3dSmooth( final int flow3dSmooth )
		{
			this.flow3dSmooth = flow3dSmooth;
			return this;
		}

		public Builder nIter( final int nIter )
		{
			this.nIter = nIter;
			return this;
		}

		public Builder torchVersion( final String torchVersion )
		{
			this.torchVersion = torchVersion;
			return this;
		}

		public Builder model( final OmniposeBuiltinModels model )
		{
			this.model = model;
			return this;
		}

		public OmniposeParameters build()
		{
			return new OmniposeParameters(
					model, channels, customModel, diameter, do3D, normalize,
					flowThreshold, cellProbThreshold, useGpu, minSize,
					anisotropy, stitchThreshold, resample, tileOverlap,
					computeFlows, flow3dSmooth, nIter, torchVersion );
		}

	}

	public static Builder builder()
	{
		return new Builder();
	}
}
