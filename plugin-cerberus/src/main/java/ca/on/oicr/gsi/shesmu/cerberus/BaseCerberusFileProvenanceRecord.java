package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.cerberus.fileprovenance.ProvenanceRecord;
import ca.on.oicr.gsi.provenance.model.LimsProvenance;
import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceValue;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

abstract class BaseCerberusFileProvenanceRecord<T extends LimsProvenance>
    implements CerberusFileProvenanceValue {
  protected final ProvenanceRecord<T> provenanceRecord;
  private final boolean stale;

  protected BaseCerberusFileProvenanceRecord(boolean stale, ProvenanceRecord<T> provenanceRecord) {
    this.stale = stale;
    this.provenanceRecord = provenanceRecord;
  }

  @Override
  public final String accession() {
    return "vidarr:"
        + provenanceRecord.workflow().getInstanceName()
        + "/"
        + provenanceRecord.record().getId();
  }

  @Override
  public final Instant completed_date() {
    return provenanceRecord.workflow().getCompleted().toInstant();
  }

  @Override
  public final Tuple external_key() {
    return new Tuple(
        provenanceRecord.lims().getProvenanceId(),
        provenanceRecord.provider(),
        stale,
        Map.of(
            "pinery-hash-" + provenanceRecord.formatRevision(),
            provenanceRecord.lims().getVersion()));
  }

  @Override
  public final Map<String, Set<String>> file_attributes() {
    return provenanceRecord.record().getLabels().entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, e -> Set.of(e.getValue())));
  }

  @Override
  public final long file_size() {
    return provenanceRecord.record().getFileSize();
  }

  @Override
  public final Set<String> input_files() {
    return new TreeSet<>(provenanceRecord.workflow().getInputFiles());
  }

  @Override
  public Tuple lims() {
    return new Tuple(
        provenanceRecord.lims().getProvenanceId(),
        provenanceRecord.provider(),
        provenanceRecord.lims().getLastModified().toInstant(),
        provenanceRecord.lims().getVersion());
  }

  @Override
  public final String md5() {
    return provenanceRecord.record().getMd5sum();
  }

  @Override
  public final String metatype() {
    return provenanceRecord.record().getMetatype();
  }

  @Override
  public final Path path() {
    return Path.of(provenanceRecord.record().getFilePath());
  }

  @Override
  public final boolean stale() {
    return stale;
  }

  @Override
  public final Instant timestamp() {
    return provenanceRecord.workflow().getCompleted().toInstant();
  }

  @Override
  public final String workflow() {
    return provenanceRecord.workflow().getWorkflowName();
  }

  @Override
  public final String workflow_accession() {
    return provenanceRecord.workflow().getWorkflowName()
        + "/"
        + provenanceRecord.workflow().getWorkflowVersion();
  }

  @Override
  public AlgebraicValue workflow_engine() {
    return new AlgebraicValue("VIDARR");
  }

  @Override
  public final String workflow_run_accession() {
    return "vidarr:"
        + provenanceRecord.workflow().getInstanceName()
        + "/"
        + provenanceRecord.workflow().getId();
  }

  @Override
  public final Map<String, Set<String>> workflow_run_attributes() {
    return provenanceRecord.workflow().getLabels().entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, e -> Set.of(e.getValue())));
  }

  @Override
  public final Tuple workflow_version() {
    return IUSUtils.parseWorkflowVersion(provenanceRecord.workflow().getWorkflowVersion())
        .orElse(IUSUtils.UNKNOWN_VERSION);
  }
}
