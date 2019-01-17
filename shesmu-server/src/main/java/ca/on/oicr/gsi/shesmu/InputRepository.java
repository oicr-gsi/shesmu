package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

/** A service class that can provide external data that can be used as input */
public interface InputRepository<V> extends LoadedConfiguration {

  /**
   * Get all the attribute sets that this instance knows about.
   *
   * <p>Duplicates are permitted, but this may be a problem for the receiving Shesmu script.
   */
  public Stream<V> stream();
}
