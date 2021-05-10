package ca.on.oicr.gsi.shesmu.core.groupers;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutputs;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.GenericReturnTypeGuarantee;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class CrossTabGrouperDefinition extends GrouperDefinition {
  public <I, O, G extends Grouper<I, ?>, C> CrossTabGrouperDefinition() {
    super(
        "crosstab",
        GrouperParameter.dynamic(
            "partition",
            GenericReturnTypeGuarantee.variable(Object.class, "𝒯").first(),
            "Which \"axis\" this row belongs to. For a simple 2D cross table, this might be a Boolean."),
        GrouperOutputs.empty(),
        CrossTabGrouper::new);
  }

  @Override
  public String description() {
    return "Constructs a cross-table by putting items into partitions then producing a group that combines every item in each partition with each item in the other partitions.";
  }
}
