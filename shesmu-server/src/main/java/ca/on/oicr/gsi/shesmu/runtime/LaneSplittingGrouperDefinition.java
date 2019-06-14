package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutput;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutputs;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.ReturnTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class LaneSplittingGrouperDefinition extends GrouperDefinition {
  public LaneSplittingGrouperDefinition() {
    super(
        "lane_splitting",
        GrouperParameter.dynamic(
            "flowcell_architecture",
            TypeGuarantee.list(TypeGuarantee.list(TypeGuarantee.LONG)),
            "A description of which lanes may be merged. A list of lists of which lanes are physically connected on the flow cell. Samples must be in the lowest numbered lane in each group."),
        GrouperParameter.dynamic(
            "is_sample", TypeGuarantee.BOOLEAN, "Whether this is a sample (rather than a lane)."),
        GrouperParameter.dynamic("lane", TypeGuarantee.LONG, "The lane number."),
        GrouperOutputs.of(
            GrouperOutput.fixed(
                "merged_lanes",
                ReturnTypeGuarantee.list(ReturnTypeGuarantee.LONG),
                "The lanes that have been grouped together.")),
        LaneSplittingGrouper::new);
  }

  @Override
  public String description() {
    return "Group rows by lanes, but combine any lanes that contain no samples with the previous lane.";
  }
}
