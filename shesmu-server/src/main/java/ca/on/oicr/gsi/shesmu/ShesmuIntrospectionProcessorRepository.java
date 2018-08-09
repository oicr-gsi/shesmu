package ca.on.oicr.gsi.shesmu;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

@MetaInfServices
public class ShesmuIntrospectionProcessorRepository implements ShesmuIntrospectionRepository {
	public static Supplier<Stream<ShesmuIntrospectionValue>> supplier = Stream::empty;

	@Override
	public Stream<ShesmuIntrospectionValue> stream() {
		return supplier.get();
	}

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

}
