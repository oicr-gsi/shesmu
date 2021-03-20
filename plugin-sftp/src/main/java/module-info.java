import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.sftp.SftpPluginType;

module ca.on.oicr.gsi.shesmu.plugin.sftp {
  exports ca.on.oicr.gsi.shesmu.sftp;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires simpleclient;
  requires sshj;

  provides PluginFileType with
      SftpPluginType;
}
