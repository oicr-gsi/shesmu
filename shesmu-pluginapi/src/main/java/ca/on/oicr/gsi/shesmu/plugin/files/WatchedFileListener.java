package ca.on.oicr.gsi.shesmu.plugin.files;

import java.util.Optional;

/** An object that will listen for updates to a file on disk */
public interface WatchedFileListener {

  /**
   * Do any startup. Do not call {@link #update()}; it will be called immediately after this method.
   */
  void start();

  void stop();

  /**
   * Processed a changed file.
   *
   * @return if empty, the file is assumed to be updated correctly; if a number is returned, this
   *     method will be called again in the specified number of minutes. This is useful if
   *     configuration talks to an external service which is failed.
   */
  Optional<Integer> update();
}
