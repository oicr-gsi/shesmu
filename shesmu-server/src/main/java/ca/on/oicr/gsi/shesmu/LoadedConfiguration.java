package ca.on.oicr.gsi.shesmu;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Report information on the status page
 */
public interface LoadedConfiguration {

	Stream<Pair<String, Map<String, String>>> listConfiguration();

}
