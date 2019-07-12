package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** IUS information from Pinery */
public final class PineryIUSValue {
  private final String bases_mask;
  private final Instant completed_date;
  private final String donor;
  private final String group_desc;
  private final String group_id;
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
  private final String targeted_resequencing;
  private final Instant timestamp;
  private final String tissue_origin;
  private final String tissue_prep;
  private final String tissue_region;
  private final String tissue_type;

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
      boolean is_sample) {
    super();
    this.path = path;
    this.organism = organism;
    this.bases_mask = bases_mask;
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
  public String donor() {
    return donor;
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
        && donor.equals(that.donor)
        && group_desc.equals(that.group_desc)
        && group_id.equals(that.group_id)
        && ius.equals(that.ius)
        && kit.equals(that.kit)
        && library_design.equals(that.library_design)
        && library_name.equals(that.library_name)
        && library_type.equals(that.library_type)
        && lims.equals(that.lims)
        && organism.equals(that.organism)
        && path.equals(that.path)
        && project.equals(that.project)
        && targeted_resequencing.equals(that.targeted_resequencing)
        && timestamp.equals(that.timestamp)
        && tissue_origin.equals(that.tissue_origin)
        && tissue_prep.equals(that.tissue_prep)
        && tissue_region.equals(that.tissue_region)
        && tissue_type.equals(that.tissue_type);
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
        donor,
        group_desc,
        group_id,
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
        targeted_resequencing,
        timestamp,
        tissue_origin,
        tissue_prep,
        tissue_region,
        tissue_type);
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

  @ShesmuVariable
  public String run_name() {
    return (String) ius.get(0);
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
}
