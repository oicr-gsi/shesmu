package ca.on.oicr.gsi.shesmu.redict;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices(PluginFileType.class)
public class RefillableDictionaryType extends PluginFileType<RefillableDictionary> {

  public RefillableDictionaryType() {
    super(MethodHandles.lookup(), RefillableDictionary.class, ".redict");
  }

  @Override
  public RefillableDictionary create(
      Path filePath, String instanceName, Definer<RefillableDictionary> definer) {
    return new RefillableDictionary(definer, filePath, instanceName);
  }
}
