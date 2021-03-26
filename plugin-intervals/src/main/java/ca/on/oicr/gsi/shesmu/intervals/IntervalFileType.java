package ca.on.oicr.gsi.shesmu.intervals;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class IntervalFileType extends PluginFileType<IntervalFile> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public IntervalFileType() {
    super(MethodHandles.lookup(), IntervalFile.class, ".intervalbed", "intervals");
  }

  @Override
  public IntervalFile create(Path filePath, String instanceName, Definer<IntervalFile> definer) {
    return new IntervalFile(filePath, instanceName);
  }
}
