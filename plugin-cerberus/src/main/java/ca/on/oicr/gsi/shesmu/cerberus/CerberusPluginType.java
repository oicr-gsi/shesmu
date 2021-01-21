package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class CerberusPluginType extends PluginFileType<CerberusPlugin> {

  public CerberusPluginType() {
    super(MethodHandles.lookup(), CerberusPlugin.class, ".cerberus", "cerberus");
  }

  @Override
  public CerberusPlugin create(
      Path filePath, String instanceName, Definer<CerberusPlugin> definer) {
    return new CerberusPlugin(filePath, instanceName, definer);
  }
}
