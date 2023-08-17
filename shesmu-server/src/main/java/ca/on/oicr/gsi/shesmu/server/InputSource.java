package ca.on.oicr.gsi.shesmu.server;

import java.util.stream.Stream;

/**
 * Define a source which provides some data for values of 'format'. Because of input source
 * federation, it may not end up being all the data represented by that name. E.g., if both a
 * cerberus-fp plugin (PluginManager implements InputSource) and a .cerberus_fp-remote
 * (AnnotatedInputFormatDefinition implements InputSource) file are provided, the two will be joined
 * and presented as one unified 'cerberus_fp' input format by the server. Individual plugins are not
 * InputSources directly, but the PluginManager is. This keeps all the reflection in one place.
 */
public interface InputSource {
  /**
   * Stream some data under the name of 'format'.
   *
   * The server will suggest whether the data should be freshly retrieved or if readStale is
   * acceptable. Plugins can choose in their implementation whether to heed this hint.
   *
   * Different parts of the server have different needs and will request different freshnesses:
   * - the /input/<format> API endpoint requests fresh data
   * - the /input/<format>/stale API endpoint requests stale data
   * - olives always request fresh data
   * - the simulator requests stale data by default, but can be set to request fresh data
   *
   * @param format name of the format requested
   * @param readStale false if fresh data requested from server
   * @return Stream of Object where Object should be the input format data class
   */
  Stream<Object> fetch(String format, boolean readStale);
}
