import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.sftp.SftpPluginType;

module ca.on.oicr.gsi.shesmu.plugin.sftp {
  exports ca.on.oicr.gsi.shesmu.sftp;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires com.hierynomus.sshj;
  requires simpleclient;
  requires org.slf4j.jul;
  requires org.slf4j;
  requires org.bouncycastle.util;
  requires org.bouncycastle.provider;
  requires org.bouncycastle.pkix;

  provides PluginFileType with
      SftpPluginType;
}
