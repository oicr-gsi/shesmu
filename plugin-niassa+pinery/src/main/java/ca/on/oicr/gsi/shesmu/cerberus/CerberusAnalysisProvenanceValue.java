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
  private static Set<Tuple> attributes(SortedMap<String, SortedSet<String>> attributes) {
    return attributes
        .entrySet()
        .stream()
        .map(e -> new Tuple(e.getKey(), e.getValue()))
        .collect(Collectors.toCollection(ATTR_TYPE::newSet));
  }

  private static final Imyhat ATTR_TYPE = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING.asList());
  private final long fileAccession;
  private Set<Tuple> fileAttributes;
  private final Path filePath;
  private long inputFile;
  private Set<Tuple> iusAttributes;
  private final Instant lastModified;
  private final Tuple lims;
  private final String md5;
  private final String metatype;
  private final String name;
  private final long runAccession;
  private final boolean skip;
  private final String status;
  private long workflowAccession;
  private Set<Tuple> workflowAttributes;
  private final String workflowName;
  private Set<Tuple> workflowRunAttributes;
  private final Tuple workflowVersion;

  public CerberusAnalysisProvenanceValue(
      AnalysisProvenance provenance, long inputFile, IusLimsKey limsKey, Runnable isBad) {
    fileAccession = provenance.getFileId();
    fileAttributes = attributes(provenance.getFileAttributes());
    filePath = Paths.get(provenance.getFilePath());
    this.inputFile = inputFile;

    iusAttributes = attributes(provenance.getIusAttributes());
    lastModified = provenance.getLastModified().toInstant();
    lims =
        new Tuple(
            limsKey.getLimsKey().getId(),
            limsKey.getLimsKey().getProvider(),
            limsKey.getLimsKey().getLastModified().toInstant(),
            limsKey.getLimsKey().getVersion());
    md5 = provenance.getFileMd5sum();
    metatype = provenance.getFileMetaType();
    name = provenance.getWorkflowRunName();
    skip =
        (provenance.getSkip() != null && provenance.getSkip())
            || Stream.of(
                    provenance.getFileAttributes(),
                    provenance.getWorkflowRunAttributes(),
                    provenance.getIusAttributes())
                .anyMatch(p -> p.containsKey("skip"));
    status = provenance.getWorkflowRunStatus();
    runAccession = provenance.getWorkflowRunId();
    workflowAccession = provenance.getWorkflowId();
    workflowAttributes = attributes(provenance.getWorkflowAttributes());
    workflowName = provenance.getWorkflowName();
    workflowRunAttributes = attributes(provenance.getWorkflowRunAttributes());
    workflowVersion =
        BaseProvenancePluginType.parseWorkflowVersion(provenance.getWorkflowVersion(), isBad);
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> attributes() {
    return workflowRunAttributes;
  }

  @ShesmuVariable
  public long file_accession() {
    return fileAccession;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> file_attributes() {
    return fileAttributes;
  }

  @ShesmuVariable
  public long input_file() {
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
  public String md5() {
    return md5;
  }

  @ShesmuVariable
  public String metatype() {
    return metatype;
  }

  @ShesmuVariable
  public String name() {
    return name;
  }

  @ShesmuVariable
  public Path path() {
    return filePath;
  }

  @ShesmuVariable
  public long run_accession() {
    return runAccession;
  }

  @ShesmuVariable
  public boolean skip() {
    return skip;
  }

  @ShesmuVariable
  public String status() {
    return status;
  }

  @ShesmuVariable
  public Instant timestamp() {
    return lastModified;
  }

  @ShesmuVariable
  public String workflow() {
    return workflowName;
  }

  @ShesmuVariable
  public long workflow_accession() {
    return workflowAccession;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> workflow_attributes() {
    return workflowAttributes;
  }

  @ShesmuVariable(type = "t3iii")
  public Tuple workflow_version() {
    return workflowVersion;
  }
}
