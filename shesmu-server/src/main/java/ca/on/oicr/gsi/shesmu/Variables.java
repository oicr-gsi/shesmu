package ca.on.oicr.gsi.shesmu;

import java.time.Instant;

/**
 * The information available to Shesmu scripts for processing
 *
 * This is one “row” in the information being fed into Shesmu
 *
 */
public final class Variables {
	private final String accession;
	private final String donor;
	private final long file_size;
	private final String group_desc;
	private final String group_id;
	private final Tuple ius;
	private final String library_design;
	private final String library_name;
	private final long library_size;
	private final String library_type;
	private final String md5;
	private final String metatype;
	private final String path;
	private final String project;
	private final String source;
	private final String targeted_resequencing;
	private final Instant timestamp;
	private final String tissue_origin;
	private final String tissue_prep;
	private final String tissue_region;
	private final String tissue_type;
	private final String workflow;
	private final String workflow_accession;
	private final Tuple workflow_version;

	public Variables(String accession, String path, String metatype, String md5, long file_size, String workflow,
			String workflow_accession, Tuple workflow_version, String project, String library_name, String donor,
			Tuple ius, String library_design, String tissue_type, String tissue_origin, String tissue_prep,
			String targeted_resequencing, String tissue_region, String group_id, String group_desc, long library_size,
			String library_type, Instant timestamp, String source) {
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
		this.timestamp = timestamp;
		this.source = source;
	}

	@Export(type = "s")
	public String accession() {
		return accession;
	}

	@Export(type = "s")
	public String donor() {
		return donor;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Variables other = (Variables) obj;
		if (accession == null) {
			if (other.accession != null) {
				return false;
			}
		} else if (!accession.equals(other.accession)) {
			return false;
		}
		if (donor == null) {
			if (other.donor != null) {
				return false;
			}
		} else if (!donor.equals(other.donor)) {
			return false;
		}
		if (file_size != other.file_size) {
			return false;
		}
		if (group_desc == null) {
			if (other.group_desc != null) {
				return false;
			}
		} else if (!group_desc.equals(other.group_desc)) {
			return false;
		}
		if (group_id == null) {
			if (other.group_id != null) {
				return false;
			}
		} else if (!group_id.equals(other.group_id)) {
			return false;
		}
		if (ius == null) {
			if (other.ius != null) {
				return false;
			}
		} else if (!ius.equals(other.ius)) {
			return false;
		}
		if (library_design == null) {
			if (other.library_design != null) {
				return false;
			}
		} else if (!library_design.equals(other.library_design)) {
			return false;
		}
		if (library_name == null) {
			if (other.library_name != null) {
				return false;
			}
		} else if (!library_name.equals(other.library_name)) {
			return false;
		}
		if (library_size != other.library_size) {
			return false;
		}
		if (library_type == null) {
			if (other.library_type != null) {
				return false;
			}
		} else if (!library_type.equals(other.library_type)) {
			return false;
		}
		if (md5 == null) {
			if (other.md5 != null) {
				return false;
			}
		} else if (!md5.equals(other.md5)) {
			return false;
		}
		if (metatype == null) {
			if (other.metatype != null) {
				return false;
			}
		} else if (!metatype.equals(other.metatype)) {
			return false;
		}
		if (path == null) {
			if (other.path != null) {
				return false;
			}
		} else if (!path.equals(other.path)) {
			return false;
		}
		if (project == null) {
			if (other.project != null) {
				return false;
			}
		} else if (!project.equals(other.project)) {
			return false;
		}
		if (source == null) {
			if (other.source != null) {
				return false;
			}
		} else if (!source.equals(other.source)) {
			return false;
		}
		if (targeted_resequencing == null) {
			if (other.targeted_resequencing != null) {
				return false;
			}
		} else if (!targeted_resequencing.equals(other.targeted_resequencing)) {
			return false;
		}
		if (timestamp == null) {
			if (other.timestamp != null) {
				return false;
			}
		} else if (!timestamp.equals(other.timestamp)) {
			return false;
		}
		if (tissue_origin == null) {
			if (other.tissue_origin != null) {
				return false;
			}
		} else if (!tissue_origin.equals(other.tissue_origin)) {
			return false;
		}
		if (tissue_prep == null) {
			if (other.tissue_prep != null) {
				return false;
			}
		} else if (!tissue_prep.equals(other.tissue_prep)) {
			return false;
		}
		if (tissue_region == null) {
			if (other.tissue_region != null) {
				return false;
			}
		} else if (!tissue_region.equals(other.tissue_region)) {
			return false;
		}
		if (tissue_type == null) {
			if (other.tissue_type != null) {
				return false;
			}
		} else if (!tissue_type.equals(other.tissue_type)) {
			return false;
		}
		if (workflow == null) {
			if (other.workflow != null) {
				return false;
			}
		} else if (!workflow.equals(other.workflow)) {
			return false;
		}
		if (workflow_accession == null) {
			if (other.workflow_accession != null) {
				return false;
			}
		} else if (!workflow_accession.equals(other.workflow_accession)) {
			return false;
		}
		if (workflow_version == null) {
			if (other.workflow_version != null) {
				return false;
			}
		} else if (!workflow_version.equals(other.workflow_version)) {
			return false;
		}
		return true;
	}

	@Export(type = "i")
	public long file_size() {
		return file_size;
	}

	@Export(type = "s")
	public String group_desc() {
		return group_desc;
	}

	@Export(type = "s")
	public String group_id() {
		return group_id;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (accession == null ? 0 : accession.hashCode());
		result = prime * result + (donor == null ? 0 : donor.hashCode());
		result = prime * result + (int) (file_size ^ file_size >>> 32);
		result = prime * result + (group_desc == null ? 0 : group_desc.hashCode());
		result = prime * result + (group_id == null ? 0 : group_id.hashCode());
		result = prime * result + (ius == null ? 0 : ius.hashCode());
		result = prime * result + (library_design == null ? 0 : library_design.hashCode());
		result = prime * result + (library_name == null ? 0 : library_name.hashCode());
		result = prime * result + (int) (library_size ^ library_size >>> 32);
		result = prime * result + (library_type == null ? 0 : library_type.hashCode());
		result = prime * result + (md5 == null ? 0 : md5.hashCode());
		result = prime * result + (metatype == null ? 0 : metatype.hashCode());
		result = prime * result + (path == null ? 0 : path.hashCode());
		result = prime * result + (project == null ? 0 : project.hashCode());
		result = prime * result + (source == null ? 0 : source.hashCode());
		result = prime * result + (targeted_resequencing == null ? 0 : targeted_resequencing.hashCode());
		result = prime * result + (timestamp == null ? 0 : timestamp.hashCode());
		result = prime * result + (tissue_origin == null ? 0 : tissue_origin.hashCode());
		result = prime * result + (tissue_prep == null ? 0 : tissue_prep.hashCode());
		result = prime * result + (tissue_region == null ? 0 : tissue_region.hashCode());
		result = prime * result + (tissue_type == null ? 0 : tissue_type.hashCode());
		result = prime * result + (workflow == null ? 0 : workflow.hashCode());
		result = prime * result + (workflow_accession == null ? 0 : workflow_accession.hashCode());
		result = prime * result + (workflow_version == null ? 0 : workflow_version.hashCode());
		return result;
	}

	@Export(type = "t3sis")
	public Tuple ius() {
		return ius;
	}

	@Export(type = "s")
	public String library_design() {
		return library_design;
	}

	@Export(type = "s")
	public String library_name() {
		return library_name;
	}

	@Export(type = "i")
	public long library_size() {
		return library_size;
	}

	@Export(type = "s")
	public String library_type() {
		return library_type;
	}

	@Export(type = "s")
	public String md5() {
		return md5;
	}

	@Export(type = "s")
	public String metatype() {
		return metatype;
	}

	@Export(type = "s")
	public String path() {
		return path;
	}

	@Export(type = "s")
	public String project() {
		return project;
	}

	@Export(type = "s")
	public String source() {
		return source;
	}

	@Export(type = "s")
	public String targeted_resequencing() {
		return targeted_resequencing;
	}

	@Export(type = "d")
	public Instant timestamp() {
		return timestamp;
	}

	@Export(type = "s")
	public String tissue_origin() {
		return tissue_origin;
	}

	@Export(type = "s")
	public String tissue_prep() {
		return tissue_prep;
	}

	@Export(type = "s")
	public String tissue_region() {
		return tissue_region;
	}

	@Export(type = "s")
	public String tissue_type() {
		return tissue_type;
	}

	@Export(type = "s")
	public String workflow() {
		return workflow;
	}

	@Export(type = "s")
	public String workflow_accession() {
		return workflow_accession;
	}

	@Export(type = "t3iii")
	public Tuple workflow_version() {
		return workflow_version;
	}
}
