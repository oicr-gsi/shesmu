package ca.on.oicr.gsi.shesmu.onlinereport;

import ca.on.oicr.gsi.shesmu.plugin.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

/** Converts Online reports into actions */
public class OnlineReportPluginType extends PluginFileType<OnlineReport> {

  @Override
  public OnlineReport create(Path filePath, String instanceName, Definer<OnlineReport> definer) {
    return new OnlineReport(filePath, instanceName, definer);
  }

  public OnlineReportPluginType() {
    super(MethodHandles.lookup(), OnlineReport.class, ".onlinereport", "onlinereport");
  }
}
