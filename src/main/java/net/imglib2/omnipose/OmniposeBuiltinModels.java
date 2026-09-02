package net.imglib2.omnipose;

public enum OmniposeBuiltinModels
{

	// Omnipose-specific bacterial models
	BACT_PHASE_AFFINITY(
			"bact_phase_affinity",
			"Newer version of the 'bact_phase_omni' model." ),
	BACT_PHASE_OMNI(
			"bact_phase_omni",
			"Omnipose bacterial segmentation model for phase-contrast / brightfield-like bacterial images." ),
	BACT_FLUOR_OMNI(
			"bact_fluor_omni",
			"Omnipose bacterial segmentation model for fluorescent bacterial images." ),
	BACT_OMNI(
			"bact_omni",
			"General Omnipose bacterial model. Prefer bact_phase_omni or bact_fluor_omni when the imaging modality is known." ),

	// Omnipose worm / C. elegans models
	WORM_OMNI(
			"worm_omni",
			"Omnipose model for C. elegans / worm segmentation." ),
	WORM_BACT_OMNI(
			"worm_bact_omni",
			"Omnipose model for segmenting bacteria in or around worms." ),
	WORM_HIGH_RES_OMNI(
			"worm_high_res_omni",
			"High-resolution Omnipose worm model." ),

	// Omnipose versions of Cellpose-style models
	CYTO2_OMNI(
			"cyto2_omni",
			"Omnipose-adapted version of the Cellpose cyto2 cytoplasm model." ),

	// Cellpose-compatible models commonly available through Omnipose
	CYTO2(
			"cyto2",
			"Cellpose2 cytoplasm model, available for compatibility." ),
	CYTO(
			"cyto",
			"Original Cellpose cytoplasm model, available for compatibility." ),
	NUCLEI(
			"nuclei",
			"Cellpose nuclear segmentation model, available for compatibility." ),

	// Cellpose-style bacterial baseline models, if present in the installed
	// Omnipose version
	BACT_PHASE_CP(
			"bact_phase_cp",
			"Cellpose-style bacterial model for phase-contrast bacterial images." ),
	BACT_FLUOR_CP(
			"bact_fluor_cp",
			"Cellpose-style bacterial model for fluorescent bacterial images." );

	private final String modelName;

	private final String description;

	OmniposeBuiltinModels( final String modelName, final String description )
	{
		this.modelName = modelName;
		this.description = description;
	}

	public String modelName()
	{
		return modelName;
	}

	public String description()
	{
		return description;
	}

	@Override
	public String toString()
	{
		return modelName;
	}

	/**
	 * Gets a tooltip string combining the model name and description
	 *
	 * @return Formatted string for tooltip display
	 */
	public String getTooltip()
	{
		return String.format( "%s: %s", modelName, description );
	}
}
