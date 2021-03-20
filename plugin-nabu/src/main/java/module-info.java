import ca.on.oicr.gsi.shesmu.nabu.NabuFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.plugin.nabu {
  exports ca.on.oicr.gsi.shesmu.nabu;

  requires ca.on.oicr.gsi.shesmu;

  provides InputFormat with
      NabuFormatDefinition;
}
