package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceSkippedValue;
import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceValue;
import java.util.List;
import java.util.stream.Stream;

final class FileProvenanceOutput {
  private final List<CerberusErrorValue> errors;
  private final List<CerberusFileProvenanceValue> fileProvenance;
  private final List<CerberusFileProvenanceSkippedValue> fileProvenanceSkipped;

  public FileProvenanceOutput(
      List<CerberusFileProvenanceValue> fileProvenance,
      List<CerberusErrorValue> errors,
      List<CerberusFileProvenanceSkippedValue> fileProvenanceSkipped) {
    this.fileProvenance = fileProvenance;
    this.errors = errors;
    this.fileProvenanceSkipped = fileProvenanceSkipped;
  }

  public Stream<CerberusErrorValue> errors() {
    return errors.stream();
  }

  public Stream<CerberusFileProvenanceValue> fileProvenance() {
    return fileProvenance.stream();
  }

  public Stream<CerberusFileProvenanceSkippedValue> fileProvenanceSkipped() {
    return fileProvenanceSkipped.stream();
  }
}
