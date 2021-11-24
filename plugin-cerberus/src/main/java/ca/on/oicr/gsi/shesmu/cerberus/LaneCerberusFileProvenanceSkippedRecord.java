package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.cerberus.fileprovenance.ProvenanceRecord;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.ws.dto.LaneProvenanceDto;
import java.util.Optional;
import java.util.Set;

public class LaneCerberusFileProvenanceSkippedRecord
    extends BaseCerberusFileProvenanceSkippedRecord<LaneProvenanceDto> {

  protected LaneCerberusFileProvenanceSkippedRecord(
      boolean stale, ProvenanceRecord<LaneProvenanceDto> provenanceRecord) {
    super(stale, provenanceRecord);
  }

  @Override
  public Optional<String> barcode_kit() {
    return Optional.empty();
  }

  @Override
  public Set<String> batches() {
    return Set.of();
  }

  @Override
  public Optional<Double> cell_viability() {
    return Optional.empty();
  }

  @Override
  public String donor() {
    return "";
  }

  @Override
  public String external_donor_id() {
    return "";
  }

  @Override
  public String external_tissue_id() {
    return "";
  }

  @Override
  public String group_desc() {
    return "";
  }

  @Override
  public String group_id() {
    return "";
  }

  @Override
  public String instrument_model() {
    return provenanceRecord.lims().getSequencerRunPlatformModel();
  }

  @Override
  public Tuple ius() {
    return new Tuple(
        provenanceRecord.lims().getSequencerRunName(),
        IUSUtils.parseLaneNumber(provenanceRecord.lims().getLaneNumber()),
        "NoIndex");
  }

  @Override
  public String kit() {
    return "";
  }

  @Override
  public String library_design() {
    return "";
  }

  @Override
  public String library_name() {
    return "";
  }

  @Override
  public long library_size() {
    return 0;
  }

  @Override
  public String library_type() {
    return "";
  }

  @Override
  public String organism() {
    return "";
  }

  @Override
  public String project() {
    return "";
  }

  @Override
  public Optional<String> reference_slide_id() {
    return Optional.empty();
  }

  @Override
  public String sequencing_control_type() {
    return "";
  }

  @Override
  public Optional<String> sex() {
    return Optional.empty();
  }

  @Override
  public boolean skip() {
    return provenanceRecord.lims().getSkip();
  }

  @Override
  public Optional<String> spike_in() {
    return Optional.empty();
  }

  @Override
  public Optional<String> spike_in_dilution_factor() {
    return Optional.empty();
  }

  @Override
  public Optional<Double> spike_in_volume_ul() {
    return Optional.empty();
  }

  @Override
  public Optional<String> subproject() {
    return Optional.empty();
  }

  @Override
  public Optional<Double> target_cell_recovery() {
    return Optional.empty();
  }

  @Override
  public String targeted_resequencing() {
    return "";
  }

  @Override
  public String tissue_name() {
    return "";
  }

  @Override
  public String tissue_origin() {
    return "";
  }

  @Override
  public String tissue_prep() {
    return "";
  }

  @Override
  public String tissue_region() {
    return "";
  }

  @Override
  public String tissue_type() {
    return "";
  }

  @Override
  public boolean umis() {
    return false;
  }
}
