package ca.on.oicr.gsi.shesmu.core.groupers;

import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutputs;

public class PowerSetGrouperDefinition extends GrouperDefinition {
  public PowerSetGrouperDefinition() {
    super("powerset", GrouperOutputs.empty(), PowerSetGrouper::new);
  }

  @Override
  public String description() {
    return "Creates groups which are the powerset of each subgroup supplied.";
  }
}
