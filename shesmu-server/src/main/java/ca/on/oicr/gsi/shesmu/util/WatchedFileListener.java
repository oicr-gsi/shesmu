package ca.on.oicr.gsi.shesmu.util;

import java.util.Optional;

public interface WatchedFileListener {

	public void start();

	public void stop();

	public Optional<Integer> update();
}
