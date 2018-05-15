package ca.on.oicr.gsi.shesmu;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Default constants provided to Shesmu scripts
 */
@MetaInfServices(ConstantSource.class)
public class StandardConstants implements ConstantSource {
	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
	private static final List<Constant> CONSTANTS = Arrays.asList(
			Constant.of("epoch", Instant.EPOCH, "The date at UNIX timestamp 0: 1970-01-01T00:00:00Z"),
			new Constant("now", Imyhat.DATE,
					"The current timestamp. This is fetched every time this constant is referenced, so now != now.") {

				@Override
				protected void load(GeneratorAdapter methodGen) {
					methodGen.invokeStatic(A_INSTANT_TYPE, METHOD_INSTANT__NOW);
				}
			});
	private static final Method METHOD_INSTANT__NOW = new Method("now", A_INSTANT_TYPE, new Type[] {});

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<Constant> queryConstants() {
		return CONSTANTS.stream();
	}

}
