package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutput;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutputs;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.GenericReturnTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.GenericTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.ReturnTypeGuarantee;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class AlwaysIncludeGrouperDefinition extends GrouperDefinition {
  public AlwaysIncludeGrouperDefinition() {
    this(GenericReturnTypeGuarantee.variable(Object.class, "𝒯"));
  }

  private <T> AlwaysIncludeGrouperDefinition(
      Pair<GenericTypeGuarantee<T>, GenericReturnTypeGuarantee<T>> t) {
    super(
        "always_include",
        GrouperParameter.dynamic(
            "key", t.first(), "The expression to get the subgrouping value for this row."),
        GrouperParameter.fixed(
            "include_when",
            t.first(),
            "If this subgrouping value is equal to this value, it will be placed in every subgroup instead of its own."),
        GrouperOutputs.of(
            GrouperOutput.fixed(
                "group_key",
                t.second(),
                "The current subgroup's key. This may be different from the row for the entries where “is_always” is true."),
            GrouperOutput.dynamic(
                "is_always",
                ReturnTypeGuarantee.BOOLEAN,
                "True if this row was added to this group because it matched the special value.")),
        AlwaysIncludeGrouper::new);
  }

  @Override
  public String description() {
    return "Performs a subgrouping operation that replicates some records. The provided “key” expression is used to create subgroups using that result as the “By” for the subgrouping. However, if that result is equal to the “include_when” value, it will be placed in every group instead of its own group.";
  }
}
