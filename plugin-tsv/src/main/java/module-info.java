import ca.on.oicr.gsi.shesmu.json.StructuredConfigFileType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.redict.RefillableDictionaryType;
import ca.on.oicr.gsi.shesmu.tsv.CSVTableFunctionFileType;
import ca.on.oicr.gsi.shesmu.tsv.EquivalenceFileType;
import ca.on.oicr.gsi.shesmu.tsv.MaintenanceSchedule;
import ca.on.oicr.gsi.shesmu.tsv.RangeFileType;
import ca.on.oicr.gsi.shesmu.tsv.StringExpandFileType;
import ca.on.oicr.gsi.shesmu.tsv.StringSetFilePlugin;
import ca.on.oicr.gsi.shesmu.tsv.TSVTableFunctionFileType;
import ca.on.oicr.gsi.shesmu.tsv.TsvDumperFileType;

module ca.on.oicr.gsi.shesmu.plugin.tsv {
  exports ca.on.oicr.gsi.shesmu.json;
  exports ca.on.oicr.gsi.shesmu.redict;
  exports ca.on.oicr.gsi.shesmu.tsv;

  requires com.fasterxml.jackson.databind;
  requires ca.on.oicr.gsi.shesmu;
  requires ca.on.oicr.gsi.serverutils;
  requires simpleclient;

  provides PluginFileType with
      CSVTableFunctionFileType,
      EquivalenceFileType,
      MaintenanceSchedule,
      RangeFileType,
      RefillableDictionaryType,
      StringExpandFileType,
      StringSetFilePlugin,
      StructuredConfigFileType,
      TSVTableFunctionFileType,
      TsvDumperFileType;
}
