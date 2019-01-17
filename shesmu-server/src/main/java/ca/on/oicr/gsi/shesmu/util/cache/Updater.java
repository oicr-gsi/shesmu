package ca.on.oicr.gsi.shesmu.util.cache;

import java.time.Instant;

public interface Updater<V> {
  V update(Instant lastModifed) throws Exception;
}
