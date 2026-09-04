package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;
import org.junit.Assert;
import org.junit.Test;

import net.imglib2.FinalDimensions;
import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * JUnit tests that check that the appose environment are correctly
 * installed/activated.
 */
public class EnvironmentTest
{

	@Test
	public void createEnvironment()
	{
		final OmniposeParameters params = OmniposeParameters.builder()
				.model( OmniposeBuiltinModels.BACT_PHASE_AFFINITY )
				.computeFlows( true )
				.channels( 0, 0 )
				.build();
		final OmniposeRunner< UnsignedByteType, UnsignedByteType > runner = OmniposeRunner.create(
				params,
				new FinalDimensions( 300, 300 ),
				AxisInfo.XY,
				new UnsignedByteType(),
				new UnsignedByteType(),
				ApposeTaskListener.STD );
		try (runner)
		{
			runner.init();
			runner.close();
		}
		catch ( BuildException | IOException | InterruptedException | TaskException e )
		{
			Assert.fail( "Got an exception when installing environment: " + e );
			e.printStackTrace();
		}
	}
}
