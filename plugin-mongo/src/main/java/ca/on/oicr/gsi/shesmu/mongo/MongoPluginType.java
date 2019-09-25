package ca.on.oicr.gsi.shesmu.mongo;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class MongoPluginType extends PluginFileType<MongoServer> {

  private static final String EXTENSION = ".mongodb";

  public MongoPluginType() {
    super(MethodHandles.lookup(), MongoServer.class, EXTENSION);
  }

  @Override
  public MongoServer create(Path filePath, String instanceName, Definer<MongoServer> definer) {
    return new MongoServer(filePath, instanceName, definer);
  }
}
