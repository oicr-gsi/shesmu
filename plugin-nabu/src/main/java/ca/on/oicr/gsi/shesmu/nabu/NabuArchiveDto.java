package ca.on.oicr.gsi.shesmu.nabu;

public interface NabuArchiveDto {
  String getCreated();

  String getFilesLoadedIntoVidarrArchival();

  String getFilesCopiedToOffsiteArchiveStagingDir();

  String getCommvaultBackupJobId();
}
