package ca.on.oicr.gsi.shesmu.util.definitions;

import ca.on.oicr.gsi.shesmu.util.FileBound;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import ca.on.oicr.gsi.status.ConfigurationSection;

public interface FileBackedConfiguration extends WatchedFileListener, FileBound {

  public ConfigurationSection configuration();
}
