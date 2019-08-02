package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
  private final Optional<Long> fileAccession;
  private Set<Tuple> fileAttributes;
  private final Optional<Path> filePath;
  private Optional<Long> inputFile;
  private Set<Tuple> iusAttributes;
  private final Instant lastModified;
  private final Tuple lims;
  private final Optional<String> md5;
  private final Optional<String> metatype;
  private final Optional<String> name;
  private final Optional<Long> runAccession;
  private final boolean skip;
  private final Optional<String> status;
  private final Optional<Long> workflowAccession;
  private final Set<Tuple> workflowAttributes;
  private final Optional<String> workflowName;
  private final Set<Tuple> workflowRunAttributes;
  private final Optional<Tuple> workflowVersion;

  public CerberusAnalysisProvenanceValue(
      AnalysisProvenance provenance,
      Optional<Long> inputFile,
      IusLimsKey limsKey,
      Set<Tuple> fileAttributes,
      Set<Tuple> iusAttributes,
      Set<Tuple> workflowAttributes,
      Set<Tuple> workflowRunAttributes,
      Runnable isBad) {
    fileAccession = Optional.ofNullable(provenance.getFileId()).map(Integer::longValue);
    this.fileAttributes = fileAttributes;
    filePath = Optional.ofNullable(provenance.getFilePath()).map(Paths::get);
    this.inputFile = inputFile;

    this.iusAttributes = iusAttributes;
    lastModified = provenance.getLastModified().toInstant();
    lims =
        new Tuple(
            limsKey.getLimsKey().getId(),
            limsKey.getLimsKey().getProvider(),
            limsKey.getLimsKey().getLastModified().toInstant(),
            limsKey.getLimsKey().getVersion());
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
    this.workflowAttributes = workflowAttributes;
    workflowName = Optional.ofNullable(provenance.getWorkflowName());
    this.workflowRunAttributes = workflowRunAttributes;
    workflowVersion =
        provenance.getWorkflowVersion() == null
            ? Optional.empty()
            : Optional.of(
                BaseProvenancePluginType.parseWorkflowVersion(
                    provenance.getWorkflowVersion(), isBad));
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
  public Optional<Long> input_file() {
    return inputFile;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> ius_attributes() {
    return iusAttributes;
  }

  @ShesmuVariable(type = "o4id$sprovider$stime$dversion$s")
  public Tuple lims() {
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
  public Instant timestamp() {
    return lastModified;
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
