package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** IUS information from Pinery */
public final class PineryIUSValue {
  private final String bases_mask;
  private final Instant completed_date;
  private final Optional<String> container_model;
  private final String donor;
  private final Optional<Double> dv200;
  private final String group_desc;
  private final String group_id;
  private final String instrumentModel;
  private final boolean is_sample;
  private final Tuple ius;
  private final String kit;
  private final String library_design;
  private final String library_name;
  private final long library_size;
  private final String library_type;
  private final Tuple lims;
  private final String organism;
  private final Path path;
  private final String project;
  private final Optional<Double> rin;
  private final String run_status;
  private final Optional<String> sequencing_kit;
  private final Optional<String> sex;
  private final Instant startDate;
  private final String targeted_resequencing;
  private final Instant timestamp;
  private final String tissue_origin;
  private final String tissue_prep;
  private final String tissue_region;
  private final String tissue_type;
  private final boolean umis;

  public PineryIUSValue(
      Path path,
      String project,
      String organism,
      String library_name,
      String donor,
      Tuple ius,
      String library_design,
      String tissue_type,
      String tissue_origin,
      String tissue_prep,
      String targeted_resequencing,
      String tissue_region,
      String group_id,
      String group_desc,
      long library_size,
      String library_type,
      String kit,
      Instant timestamp,
      Tuple lims,
      Instant completed_date,
      String bases_mask,
      String instrumentModel,
      Optional<Double> dv200,
      Optional<Double> rin,
      String run_status,
      boolean umis,
      Optional<String> sequencing_kit,
      Optional<String> container_model,
      Optional<String> sex,
      Instant startDate,
      boolean is_sample) {
    super();
    this.path = path;
    this.organism = organism;
    this.bases_mask = bases_mask;
    this.instrumentModel = instrumentModel;
    this.sex = sex;
    this.startDate = startDate;
    this.is_sample = is_sample;
    this.project = project;
    this.library_name = library_name;
    this.donor = donor;
    this.ius = ius;
    this.library_design = library_design;
    this.tissue_type = tissue_type;
    this.tissue_origin = tissue_origin;
    this.tissue_prep = tissue_prep;
    this.targeted_resequencing = targeted_resequencing;
    this.tissue_region = tissue_region;
    this.group_id = group_id;
    this.group_desc = group_desc;
    this.library_size = library_size;
    this.library_type = library_type;
    this.kit = kit;
    this.timestamp = timestamp;
    this.lims = lims;
    this.completed_date = completed_date;
    this.dv200 = dv200;
    this.rin = rin;
    this.run_status = run_status;
    this.umis = umis;
    this.sequencing_kit = sequencing_kit;
    this.container_model = container_model;
  }

  @ShesmuVariable(signable = true)
  public String bases_mask() {
    return bases_mask;
  }

  @ShesmuVariable
  public Instant completed_date() {
    return completed_date;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> container_model() {
    return container_model;
  }

  @ShesmuVariable(signable = true)
  public String donor() {
    return donor;
  }

  @ShesmuVariable(signable = true)
  public Optional<Double> dv200() {
    return dv200;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PineryIUSValue that = (PineryIUSValue) o;
    return is_sample == that.is_sample
        && library_size == that.library_size
        && bases_mask.equals(that.bases_mask)
        && completed_date.equals(that.completed_date)
        && container_model.equals(that.container_model)
        && donor.equals(that.donor)
        && dv200.equals(that.dv200)
        && group_desc.equals(that.group_desc)
        && group_id.equals(that.group_id)
        && instrumentModel.equals(that.instrumentModel)
        && ius.equals(that.ius)
        && kit.equals(that.kit)
        && library_design.equals(that.library_design)
        && library_name.equals(that.library_name)
        && library_type.equals(that.library_type)
        && lims.equals(that.lims)
        && organism.equals(that.organism)
        && path.equals(that.path)
        && project.equals(that.project)
        && rin.equals(that.rin)
        && run_status.equals(that.run_status)
        && sequencing_kit.equals(that.sequencing_kit)
        && sex.equals(that.sex)
        && startDate.equals(that.startDate)
        && targeted_resequencing.equals(that.targeted_resequencing)
        && timestamp.equals(that.timestamp)
        && tissue_origin.equals(that.tissue_origin)
        && tissue_prep.equals(that.tissue_prep)
        && tissue_region.equals(that.tissue_region)
        && tissue_type.equals(that.tissue_type)
        && umis == that.umis;
  }

  @ShesmuVariable(signable = true)
  public String group_desc() {
    return group_desc;
  }

  @ShesmuVariable(signable = true)
  public String group_id() {
    return group_id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        bases_mask,
        completed_date,
        container_model,
        donor,
        dv200,
        group_desc,
        group_id,
        instrumentModel,
        is_sample,
        ius,
        kit,
        library_design,
        library_name,
        library_size,
        library_type,
        lims,
        organism,
        path,
        project,
        rin,
        run_status,
        sequencing_kit,
        sex,
        startDate,
        targeted_resequencing,
        timestamp,
        tissue_origin,
        tissue_prep,
        tissue_region,
        tissue_type,
        umis);
  }

  @ShesmuVariable(signable = true)
  public String instrument_model() {
    return instrumentModel;
  }

  @ShesmuVariable
  public boolean is_sample() {
    return is_sample;
  }

  @ShesmuVariable(type = "t3sis")
  public Tuple ius() {
    return ius;
  }

  @ShesmuVariable(signable = true)
  public String kit() {
    return kit;
  }

  @ShesmuVariable(signable = true)
  public String library_design() {
    return library_design;
  }

  @ShesmuVariable(signable = true)
  public String library_name() {
    return library_name;
  }

  @ShesmuVariable(signable = true)
  public long library_size() {
    return library_size;
  }

  @ShesmuVariable(signable = true)
  public String library_type() {
    return library_type;
  }

  @ShesmuVariable(type = "o4id$sprovider$stime$dversion$s")
  public Tuple lims() {
    return lims;
  }

  @ShesmuVariable(signable = true)
  public String organism() {
    return organism;
  }

  @ShesmuVariable
  public Path path() {
    return path;
  }

  @ShesmuVariable(signable = true)
  public String project() {
    return project;
  }

  @ShesmuVariable(signable = true)
  public Optional<Double> rin() {
    return rin;
  }

  @ShesmuVariable(signable = true)
  public String run_name() {
    return (String) ius.get(0);
  }

  @ShesmuVariable
  public String run_status() {
    return run_status;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> sequencing_kit() {
    return sequencing_kit;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> sex() {
    return sex;
  }

  @ShesmuVariable
  public Instant start_date() {
    return startDate;
  }

  @ShesmuVariable(signable = true)
  public String targeted_resequencing() {
    return targeted_resequencing;
  }

  @ShesmuVariable
  public Instant timestamp() {
    return timestamp;
  }

  @ShesmuVariable(signable = true)
  public String tissue_origin() {
    return tissue_origin;
  }

  @ShesmuVariable(signable = true)
  public String tissue_prep() {
    return tissue_prep;
  }

  @ShesmuVariable(signable = true)
  public String tissue_region() {
    return tissue_region;
  }

  @ShesmuVariable(signable = true)
  public String tissue_type() {
    return tissue_type;
  }

  @ShesmuVariable(signable = true)
  public boolean umis() {
    return umis;
  }
}
