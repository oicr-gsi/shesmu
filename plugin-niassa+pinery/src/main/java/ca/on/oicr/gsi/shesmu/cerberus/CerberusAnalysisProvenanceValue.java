package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The information available to Shesmu scripts for processing
 *
 * <p>This is one “row” in the information being fed into Shesmu
 */
public final class CerberusAnalysisProvenanceValue {
  public static Set<Tuple> attributes(SortedMap<String, SortedSet<String>> attributes) {
    if (attributes == null) {
      return Collections.emptySet();
    }
    return attributes
        .entrySet()
        .stream()
        .map(e -> new Tuple(e.getKey(), e.getValue()))
        .collect(Collectors.toCollection(ATTR_TYPE::newSet));
  }

  private static final Imyhat ATTR_TYPE = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING.asList());
  private static final Imyhat LIMS_TYPE =
      new Imyhat.ObjectImyhat(
          Stream.of(
              new Pair<>("id", Imyhat.STRING),
              new Pair<>("provider", Imyhat.STRING),
              new Pair<>("time", Imyhat.DATE),
              new Pair<>("version", Imyhat.STRING)));
  private final Optional<Long> fileAccession;
  private Set<Tuple> fileAttributes;
  private final Optional<Path> filePath;
  private Set<Long> inputFiles;
  private Set<Tuple> iusAttributes;
  private final Set<Tuple> lims;
  private final Optional<String> md5;
  private final Optional<String> metatype;
  private final Optional<String> name;
  private final Optional<Long> runAccession;
  private final boolean skip;
  private final Optional<String> status;
  private final Optional<Instant> timestamp;
  private final Optional<Long> workflowAccession;
  private final Set<Tuple> workflowAttributes;
  private final Optional<String> workflowName;
  private final Set<Tuple> workflowRunAttributes;
  private final Optional<Tuple> workflowVersion;

  public CerberusAnalysisProvenanceValue(AnalysisProvenance provenance) {
    fileAccession = Optional.ofNullable(provenance.getFileId()).map(Integer::longValue);
    this.fileAttributes = attributes(provenance.getFileAttributes());
    filePath = Optional.ofNullable(provenance.getFilePath()).map(Paths::get);
    this.inputFiles =
        provenance.getWorkflowRunInputFileIds() == null
            ? Collections.emptySet()
            : provenance
                .getWorkflowRunInputFileIds()
                .stream()
                .map(Integer::longValue)
                .collect(Collectors.toCollection(TreeSet::new));
    this.iusAttributes = attributes(provenance.getIusAttributes());
    timestamp = Optional.ofNullable(provenance.getLastModified()).map(ZonedDateTime::toInstant);
    lims =
        provenance.getIusLimsKeys() == null
            ? Collections.emptySet()
            : provenance
                .getIusLimsKeys()
                .stream()
                .map(
                    limsKey ->
                        new Tuple(
                            limsKey.getLimsKey().getId(),
                            limsKey.getLimsKey().getProvider(),
                            limsKey.getLimsKey().getLastModified().toInstant(),
                            limsKey.getLimsKey().getVersion()))
                .collect(Collectors.toCollection(LIMS_TYPE::newSet));
    md5 = Optional.ofNullable(provenance.getFileMd5sum());
    metatype = Optional.ofNullable(provenance.getFileMetaType());
    name = Optional.ofNullable(provenance.getWorkflowRunName());
    skip =
        (provenance.getSkip() != null && provenance.getSkip())
            || Stream.of(
                    provenance.getFileAttributes(),
                    provenance.getWorkflowRunAttributes(),
                    provenance.getIusAttributes())
                .filter(Objects::nonNull)
                .anyMatch(p -> p.containsKey("skip"));
    status = Optional.ofNullable(provenance.getWorkflowRunStatus());
    runAccession = Optional.ofNullable(provenance.getWorkflowRunId()).map(Integer::longValue);
    workflowAccession = Optional.ofNullable(provenance.getWorkflowId()).map(Integer::longValue);
    this.workflowAttributes = attributes(provenance.getWorkflowAttributes());
    workflowName = Optional.ofNullable(provenance.getWorkflowName());
    this.workflowRunAttributes = attributes(provenance.getWorkflowRunAttributes());
    workflowVersion =
        provenance.getWorkflowVersion() == null
            ? Optional.empty()
            : Optional.of(
                BaseProvenancePluginType.parseWorkflowVersion(
                    provenance.getWorkflowVersion(), () -> {}));
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> attributes() {
    return workflowRunAttributes;
  }

  @ShesmuVariable
  public Optional<Long> file_accession() {
    return fileAccession;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> file_attributes() {
    return fileAttributes;
  }

  @ShesmuVariable
  public Set<Long> input_files() {
    return inputFiles;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> ius_attributes() {
    return iusAttributes;
  }

  @ShesmuVariable(type = "ao4id$sprovider$stime$dversion$s")
  public Set<Tuple> lims() {
    return lims;
  }

  @ShesmuVariable
  public Optional<String> md5() {
    return md5;
  }

  @ShesmuVariable
  public Optional<String> metatype() {
    return metatype;
  }

  @ShesmuVariable
  public Optional<String> name() {
    return name;
  }

  @ShesmuVariable
  public Optional<Path> path() {
    return filePath;
  }

  @ShesmuVariable
  public Optional<Long> run_accession() {
    return runAccession;
  }

  @ShesmuVariable
  public boolean skip() {
    return skip;
  }

  @ShesmuVariable
  public Optional<String> status() {
    return status;
  }

  @ShesmuVariable
  public Optional<Instant> timestamp() {
    return timestamp;
  }

  @ShesmuVariable
  public Optional<String> workflow() {
    return workflowName;
  }

  @ShesmuVariable
  public Optional<Long> workflow_accession() {
    return workflowAccession;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> workflow_attributes() {
    return workflowAttributes;
  }

  @ShesmuVariable(type = "qt3iii")
  public Optional<Tuple> workflow_version() {
    return workflowVersion;
  }
}
