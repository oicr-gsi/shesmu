package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * The information available to Shesmu scripts for processing
 *
 * <p>This is one “row” in the information being fed into Shesmu
 */
public final class CerberusFileProvenanceValue {
  private final String accession;
  private final Instant completed_date;
  private final String donor;
  private final long file_size;
  private final String group_desc;
  private final String group_id;
  private final Tuple ius;
  private final String kit;
  private final String library_design;
  private final String library_name;
  private final long library_size;
  private final String library_type;
  private final Tuple lims;
  private final String md5;
  private final String metatype;
  private final String organism;
  private final Path path;
  private final String project;
  private final String source;
  private final boolean stale;
  private final String targeted_resequencing;
  private final Instant timestamp;
  private final String tissue_origin;
  private final String tissue_prep;
  private final String tissue_region;
  private final String tissue_type;
  private final String workflow;
  private final String workflow_accession;
  private final Tuple workflow_version;

  public CerberusFileProvenanceValue(
      String accession,
      Path path,
      String metatype,
      String md5,
      long file_size,
      String workflow,
      String workflow_accession,
      Tuple workflow_version,
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
      boolean stale,
      String source) {
    super();
    this.accession = accession;
    this.path = path;
    this.metatype = metatype;
    this.md5 = md5;
    this.file_size = file_size;
    this.workflow = workflow;
    this.workflow_accession = workflow_accession;
    this.workflow_version = workflow_version;
    this.project = project;
    this.organism = organism;
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
    this.stale = stale;
    this.source = source;
  }

  @ShesmuVariable
  public String accession() {
    return accession;
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
    CerberusFileProvenanceValue that = (CerberusFileProvenanceValue) o;
    return file_size == that.file_size
        && library_size == that.library_size
        && stale == that.stale
        && accession.equals(that.accession)
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
        && md5.equals(that.md5)
        && metatype.equals(that.metatype)
        && organism.equals(that.organism)
        && path.equals(that.path)
        && project.equals(that.project)
        && source.equals(that.source)
        && targeted_resequencing.equals(that.targeted_resequencing)
        && timestamp.equals(that.timestamp)
        && tissue_origin.equals(that.tissue_origin)
        && tissue_prep.equals(that.tissue_prep)
        && tissue_region.equals(that.tissue_region)
        && tissue_type.equals(that.tissue_type)
        && workflow.equals(that.workflow)
        && workflow_accession.equals(that.workflow_accession)
        && workflow_version.equals(that.workflow_version);
  }

  @ShesmuVariable
  public long file_size() {
    return file_size;
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
        accession,
        completed_date,
        donor,
        file_size,
        group_desc,
        group_id,
        ius,
        kit,
        library_design,
        library_name,
        library_size,
        library_type,
        lims,
        md5,
        metatype,
        organism,
        path,
        project,
        source,
        stale,
        targeted_resequencing,
        timestamp,
        tissue_origin,
        tissue_prep,
        tissue_region,
        tissue_type,
        workflow,
        workflow_accession,
        workflow_version);
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

  @ShesmuVariable(type = "t4sssd")
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
  public String source() {
    return source;
  }

  @ShesmuVariable
  public boolean stale() {
    return stale;
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

  @ShesmuVariable
  public String workflow() {
    return workflow;
  }

  @ShesmuVariable
  public String workflow_accession() {
    return workflow_accession;
  }

  @ShesmuVariable(type = "t3iii")
  public Tuple workflow_version() {
    return workflow_version;
  }
}
