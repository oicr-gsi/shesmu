package ca.on.oicr.gsi.shesmu.core.groupers;

import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutputs;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;

public final class CombinationsGrouperDefinition extends GrouperDefinition {
  public CombinationsGrouperDefinition() {
    super(
        "combinations",
        GrouperParameter.fixed(
            "group_size",
            TypeGuarantee.LONG,
            "The number of elements to include in the output groups (e.g., 2 for all pairs)."),
        GrouperOutputs.empty(),
        CombinationsGrouper::new);
  }

  @Override
  public String description() {
    return "Creates all combinations of the input of a particular group size.";
  }
}
