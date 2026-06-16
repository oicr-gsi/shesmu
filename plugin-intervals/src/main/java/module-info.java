import ca.on.oicr.gsi.shesmu.genomeidx.GenomeIndexFileType;
import ca.on.oicr.gsi.shesmu.intervals.IntervalFileType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;

module ca.on.oicr.gsi.shesmu.plugin.intervals {
  exports ca.on.oicr.gsi.shesmu.intervals;
  exports ca.on.oicr.gsi.shesmu.genomeidx;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires tools.jackson.databind;

  provides PluginFileType with
      IntervalFileType,
      GenomeIndexFileType;
}
