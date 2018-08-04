package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

public interface SourceLocationLinker extends LoadedConfiguration {
	/**
	 * Create a URL for a source file
	 * 
	 * @param location
	 *            the location of the source file
	 * @return the URL to the source file or null if not possible
	 */
	Stream<String> url(SourceLocation location);
}
