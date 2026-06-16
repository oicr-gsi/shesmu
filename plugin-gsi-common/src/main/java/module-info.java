import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceFormatDefinition;
import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceSkippedFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.gsi.common {
  exports ca.on.oicr.gsi.shesmu.gsicommon;

  requires ca.on.oicr.gsi.shesmu;
  requires tools.jackson.databind;

  provides InputFormat with
      CerberusFileProvenanceFormatDefinition,
      CerberusFileProvenanceSkippedFormatDefinition;
}
