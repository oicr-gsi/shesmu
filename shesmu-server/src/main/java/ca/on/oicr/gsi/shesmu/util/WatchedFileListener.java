package ca.on.oicr.gsi.shesmu.util;

import java.util.Optional;

/** An object that will listen for updates to a file on disk */
public interface WatchedFileListener {

  public void start();

  public void stop();

  /**
   * Processed a changed file.
   *
   * @return if empty, the file is assumed to be updated correctly; if a number is returned, this
   *     method will be called again in the specified number of minutes. This is useful if
   *     configuration talks to an external service which is failed.
   */
  public Optional<Integer> update();
}
