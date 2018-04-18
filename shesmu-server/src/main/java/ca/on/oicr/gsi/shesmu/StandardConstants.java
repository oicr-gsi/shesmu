package ca.on.oicr.gsi.shesmu;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

/**
 * Default constants provided to Shesmu scripts
 */
@MetaInfServices(ConstantSource.class)
public class StandardConstants implements ConstantSource {
	private static final List<Constant> CONSTANTS = Arrays.asList(Constant.of("epoch", Instant.EPOCH));

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<Constant> queryConstants() {
		return CONSTANTS.stream();
	}

}
