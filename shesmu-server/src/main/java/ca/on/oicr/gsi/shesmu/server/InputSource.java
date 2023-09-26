package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.server.plugins.PluginManager;
import java.util.stream.Stream;

/**
 * Define a source which provides some data for values of 'format'. Because of input source
 * federation, it may not end up being all the data represented by that name. <em>e.g.</em>, if both
 * a <code>cerberus-fp</code> plugin ({@link PluginManager} provides an <code>InputSource</code>)
 * and a <code>.cerberus_fp-remote</code> ({@link AnnotatedInputFormatDefinition} implements <code>
 * InputSource</code>) file are provided, the two will be joined and presented as one unified <code>
 * cerberus_fp</code> input format by the server. Individual plugins are not input sources directly,
 * but the {@link PluginManager} is. This keeps all the reflection in one place.
 */
public interface InputSource {
  /**
   * Stream some data under the name of <em>format</em>.
   *
   * <p>The server will suggest whether the data should be freshly retrieved or if readStale is
   * acceptable. Plugins can choose in their implementation whether to heed this hint.
   *
   * <p>Different parts of the server have different needs and will request different freshnesses:
   *
   * <ul>
   *   <li>the <code>/input/</code><em>format</em> API endpoint requests fresh data
   *   <li>the <code>/input/</code><em>format</em><code>/stale</code> API endpoint requests stale
   *       data
   *   <li>olives always request fresh data
   *   <li>the simulator requests stale data by default, but can be set to request fresh data
   * </ul>
   *
   * @param format name of the format requested
   * @param readStale false if fresh data requested from server
   * @return stream of records, which can be a mix of instances of the input format data object and
   *     a tuples containing all the fields in alphabetical order
   */
  Stream<Object> fetch(String format, boolean readStale);
}
