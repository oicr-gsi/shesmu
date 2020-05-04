package ca.on.oicr.gsi.shesmu.plugin.input.unixfs;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import java.nio.file.Path;
import java.time.Instant;

public interface UnixFileData {
  @ShesmuVariable(timeFormat = TimeFormat.SECONDS_NUMERIC)
  Instant atime();

  @ShesmuVariable(timeFormat = TimeFormat.SECONDS_NUMERIC)
  Instant ctime();

  @ShesmuVariable
  Path file();

  @ShesmuVariable
  String group();

  @ShesmuVariable
  String host();

  @ShesmuVariable(timeFormat = TimeFormat.SECONDS_NUMERIC)
  Instant mtime();

  @ShesmuVariable
  long perms();

  @ShesmuVariable
  long size();

  @ShesmuVariable
  String user();
}
