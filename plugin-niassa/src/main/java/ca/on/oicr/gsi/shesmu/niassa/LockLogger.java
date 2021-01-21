package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;

public interface LockLogger {
  void log(LimsKey key, String message);
}
