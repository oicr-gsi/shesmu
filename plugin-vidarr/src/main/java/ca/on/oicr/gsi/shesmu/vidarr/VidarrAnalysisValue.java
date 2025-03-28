package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class VidarrAnalysisValue {
  private final Optional<Instant> completed_date;
  private final Set<Tuple> output_files;
  private final Set<Tuple> workflow_run_external_keys;
  private final Set<String> input_files;
  private final String workflow;
  private final String workflow_accession;
  private final String workflow_run_accession;
  private final Map<String, JsonNode> workflow_run_labels;
  private final Tuple workflow_version;

  public VidarrAnalysisValue(
      Optional<Instant> completed_date,
      Set<Tuple> output_files,
      Set<Tuple> workflow_run_external_keys,
      Set<String> input_files,
      String workflow,
      String workflow_accession,
      String workflow_run_accession,
      Map<String, JsonNode> workflow_run_labels,
      Tuple workflow_version) {
    super();
    this.completed_date = completed_date;
    this.output_files = output_files;
    this.input_files = input_files;
    this.workflow = workflow;
    this.workflow_accession = workflow_accession;
    this.workflow_run_accession = workflow_run_accession;
    this.workflow_run_external_keys = workflow_run_external_keys;
    this.workflow_run_labels = workflow_run_labels;
    this.workflow_version = workflow_version;
  }

  @ShesmuVariable
  public Optional<Instant> completed_date() {
    return completed_date;
  }

  @ShesmuVariable
  public Set<String> input_files() {
    return input_files;
  }

  @ShesmuVariable
  public String workflow() {
    return workflow;
  }

  @ShesmuVariable
  public String workflow_accession() {
    return workflow_accession;
  }

  @ShesmuVariable(type = "ao3id$sprovider$sversions$mss")
  public Set<Tuple> workflow_run_external_keys() {
    return workflow_run_external_keys;
  }

  @ShesmuVariable
  public String workflow_run_accession() {
    return workflow_run_accession;
  }

  @ShesmuVariable
  public Map<String, JsonNode> workflow_run_labels() {
    return workflow_run_labels;
  }

  @ShesmuVariable(type = "t3iii")
  public Tuple workflow_version() {
    return workflow_version;
  }

  @ShesmuVariable(
      type =
          "ao8checksum$schecksum_type$sexternal_keys$ao2accession$sprovider$sfile_attributes$mssfile_size$iid$smetatype$spath$s")
  public Set<Tuple> output_files() {
    return output_files;
  }
}
