package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.cerberus.fileprovenance.ProvenanceRecord;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.ws.dto.SampleProvenanceDto;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SampleCerberusFileProvenanceRecord
    extends BaseCerberusFileProvenanceRecord<SampleProvenanceDto> {

  private static final Pattern COMMA = Pattern.compile(",");

  protected SampleCerberusFileProvenanceRecord(
      boolean stale, ProvenanceRecord<SampleProvenanceDto> provenanceRecord) {
    super(stale, provenanceRecord);
  }

  @Override
  public Optional<String> barcode_kit() {
    return limsAttr("barcode_kit");
  }

  @Override
  public Set<String> batches() {
    return limsAttr("batches")
        .<Set<String>>map(
            s -> COMMA.splitAsStream(s).collect(Collectors.toCollection(TreeSet::new)))
        .orElse(Set.of());
  }

  @Override
  public Optional<Double> cell_viability() {
    return limsAttr("cell_viability").map(Double::parseDouble);
  }

  @Override
  public String donor() {
    return provenanceRecord.lims().getRootSampleName();
  }

  @Override
  public String external_donor_id() {
    return limsAttr("geo_external_name").orElse("");
  }

  @Override
  public String external_tissue_id() {
    return limsAttr("geo_tube_id").orElse("");
  }

  @Override
  public String group_desc() {
    return limsAttr("geo_group_id_description").orElse("");
  }

  @Override
  public String group_id() {
    return limsAttr("geo_group_id").orElse("");
  }

  @Override
  public String instrument_model() {
    return provenanceRecord.lims().getSequencerRunName();
  }

  @Override
  public Tuple ius() {
    return new Tuple(
        provenanceRecord.lims().getSequencerRunName(),
        IUSUtils.parseLaneNumber(provenanceRecord.lims().getLaneNumber()),
        provenanceRecord.lims().getIusTag());
  }

  @Override
  public String kit() {
    return limsAttr("geo_prep_kit").orElse("");
  }

  @Override
  public String library_design() {
    return limsAttr("geo_library_source_template_type").orElse("");
  }

  @Override
  public String library_name() {
    return provenanceRecord.lims().getSampleName();
  }

  @Override
  public long library_size() {
    return limsAttr("geo_library_size_code").map(Long::parseLong).orElse(0L);
  }

  @Override
  public String library_type() {
    return limsAttr("geo_library_type").orElse("");
  }

  private Optional<String> limsAttr(String key) {
    return provenanceRecord.lims().getSampleAttributes()
        .getOrDefault(key, Collections.emptySortedSet()).stream()
        .findAny();
  }

  @Override
  public String organism() {
    return limsAttr("geo_organism").orElse("");
  }

  @Override
  public String project() {
    return provenanceRecord.lims().getStudyTitle();
  }

  @Override
  public Optional<String> reference_slide_id() {
    return limsAttr("reference_slide_id");
  }

  @Override
  public String sequencing_control_type() {
    return limsAttr("sequencing_control_type").orElse("");
  }

  @Override
  public Optional<String> sex() {
    return limsAttr("sex");
  }

  @Override
  public Optional<String> spike_in() {
    return limsAttr("spike_in");
  }

  @Override
  public Optional<String> spike_in_dilution_factor() {
    return limsAttr("spike_in_dilution_factor");
  }

  @Override
  public Optional<Double> spike_in_volume_ul() {
    return limsAttr("spike_in_volume_ul").map(Double::parseDouble);
  }

  @Override
  public Optional<String> subproject() {
    return limsAttr("subproject").filter(p -> !p.isBlank());
  }

  @Override
  public Optional<Double> target_cell_recovery() {
    return limsAttr("target_cell_recovery").map(Double::parseDouble);
  }

  @Override
  public String targeted_resequencing() {
    return limsAttr("geo_targeted_resequencing").orElse("");
  }

  @Override
  public String tissue_name() {
    return IUSUtils.tissue(provenanceRecord.lims().getParentSampleName());
  }

  @Override
  public String tissue_origin() {
    return limsAttr("geo_tissue_origin").orElse("");
  }

  @Override
  public String tissue_prep() {
    return limsAttr("geo_tissue_preparation").orElse("");
  }

  @Override
  public String tissue_region() {
    return limsAttr("geo_tissue_region").orElse("");
  }

  @Override
  public String tissue_type() {
    return limsAttr("geo_tissue_type").orElse("");
  }

  @Override
  public boolean umis() {
    return limsAttr("umis").map("true"::equalsIgnoreCase).orElse(false);
  }
}
