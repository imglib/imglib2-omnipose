package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;
import org.junit.Assert;
import org.junit.Test;

import net.imglib2.appose.ShmImg;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * JUnit tests that check that the appose environment are correctly installed/activated.
 */
public class EnvironmentTest
{

	@Test
	public void createEnvironment()
	{
		final int[] dims = new int[] { 300, 300 };
		try {
			final ShmImg<UnsignedByteType> shimg = new ShmImg<>( new UnsignedByteType(), dims );
			final ShmImg<UnsignedByteType> shout = new ShmImg<>( new UnsignedByteType(), dims );


			final OmniposeParameters params = OmniposeParameters.builder()
					.model( OmniposeBuiltinModels.BACT_PHASE_AFFINITY )
				.computeFlows( true )
				.channels( 0, 0 )
				.build();
			final String envName = "omnipose-cpu";
			final String pythonScriptPath = "/omnipose.py";
			final String pythonInitScriptPath = "/omnipose_init.py";

			final OmniposeRunner< UnsignedByteType, UnsignedByteType > cprun = new OmniposeRunner<>(
					params,
					pythonInitScriptPath,
					pythonScriptPath,
					envName,
					ApposeTaskListener.STD,
					shimg,
					AxisInfo.XY,
					shout,
					null );

				cprun.init();
				cprun.close();

		}
		catch ( BuildException | IOException | InterruptedException | TaskException e )
		{
			Assert.fail( "Got an exception when installing environment: " + e );
			e.printStackTrace();
		}

	}
}
