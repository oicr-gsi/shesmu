package ca.on.oicr.gsi.shesmu.util;

import java.util.Optional;

/**
 * An object that will listen for updates to a file on disk
 */
public interface WatchedFileListener {

	public void start();

	public void stop();

	public Optional<Integer> update();
}
