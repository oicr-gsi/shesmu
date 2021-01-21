package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceValue;
import java.util.List;
import java.util.stream.Stream;

final class FileProvenanceOutput {
  private final List<CerberusErrorValue> errors;
  private final List<CerberusFileProvenanceValue> fileProvenance;

  public FileProvenanceOutput(List<CerberusFileProvenanceValue> fileProvenance,
      List<CerberusErrorValue> errors) {
    this.fileProvenance = fileProvenance;
    this.errors = errors;
  }

  public Stream<CerberusErrorValue> errors() {
    return errors.stream();
  }

  public Stream<CerberusFileProvenanceValue> fileProvenance() {
    return fileProvenance.stream();
  }
}
