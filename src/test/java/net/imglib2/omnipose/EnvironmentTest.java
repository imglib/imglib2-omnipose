package net.imglib2.omnipose;

import java.io.IOException;

import org.apposed.appose.BuildException;
import org.apposed.appose.TaskException;
import org.junit.Assert;
import org.junit.Test;

import net.imglib2.appose.util.ApposeTaskListener;
import net.imglib2.appose.util.AxisInfo;
import net.imglib2.img.array.ArrayImgs;

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
		final OmniposeRunner2 runner = OmniposeRunner2.create( ApposeTaskListener.STD, params.torchVersion );
		try (runner)
		{
			runner.setInput( ArrayImgs.unsignedBytes( 128, 128 ), AxisInfo.XY );
			runner.init();
			runner.run( params );
			runner.close();
		}
		catch ( BuildException | IOException | InterruptedException | TaskException e )
		{
			Assert.fail( "Got an exception when installing environment: " + e );
			e.printStackTrace();
		}
	}
}
