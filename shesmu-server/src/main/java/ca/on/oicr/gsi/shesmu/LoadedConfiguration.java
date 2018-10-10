package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

import ca.on.oicr.gsi.status.ConfigurationSection;

/**
 * Report information on the status page
 */
public interface LoadedConfiguration {

	/**
	 * Describe a list of configuration blocks for the status page
	 *
	 * @return a stream of pairs; each pair is the title of the block and a map of
	 *         rows to be displayed
	 */
	Stream<ConfigurationSection> listConfiguration();

}
