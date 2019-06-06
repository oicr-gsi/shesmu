package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutput;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperOutputs;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.ReturnTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class BarcodeGrouperDefinition extends GrouperDefinition {
  public BarcodeGrouperDefinition() {
    super(
        "barcodes",
        GrouperParameter.fixed(
            "edit_distance", TypeGuarantee.LONG, "The minimum allowed edit distance."),
        GrouperParameter.dynamic("bases_mask", TypeGuarantee.STRING, "The bases mask string."),
        GrouperParameter.dynamic(
            "barcodes",
            TypeGuarantee.list(TypeGuarantee.STRING),
            "The set of barcodes for this row."),
        GrouperOutputs.of(
            GrouperOutput.fixed(
                "barcode_length",
                ReturnTypeGuarantee.STRING,
                "A human-friendly description of the barcode length."),
            GrouperOutput.dynamic(
                "trimmed_barcodes",
                ReturnTypeGuarantee.list(ReturnTypeGuarantee.STRING),
                "The trimmed barcodes for the sample sheet.")),
        BarcodeGrouper::new);
  }

  @Override
  public String description() {
    return "Separate a barcodes into groups with a common length and the correct bases mask for that length.";
  }
}
