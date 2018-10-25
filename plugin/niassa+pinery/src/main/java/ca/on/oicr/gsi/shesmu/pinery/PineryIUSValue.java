package ca.on.oicr.gsi.shesmu.pinery;

import java.time.Instant;

import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.input.ShesmuVariable;

/**
 * IUS information from Pinery
 *
 */
public final class PineryIUSValue {
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
	private final String path;
	private final String project;
	private final String targeted_resequencing;
	private final Instant timestamp;
	private final String tissue_origin;
	private final String tissue_prep;
	private final String tissue_region;
	private final String tissue_type;

	public PineryIUSValue(//
			String path, //
			String project, //
			String library_name, //
			String donor, Tuple ius, //
			String library_design, //
			String tissue_type, //
			String tissue_origin, //
			String tissue_prep, //
			String targeted_resequencing, //
			String tissue_region, //
			String group_id, //
			String group_desc, //
			long library_size, //
			String library_type, //
			String kit, //
			Instant timestamp, //
			Tuple lims, //
			Instant completed_date, //
			boolean is_sample) {
		super();
		this.path = path;
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

	@ShesmuVariable
	public Instant completed_date() {
		return completed_date;
	}

	@ShesmuVariable(signable = true)
	public String donor() {
		return donor;
	}

	@SuppressWarnings("checkstyle:CyclomaticComplexity")
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
		final PineryIUSValue other = (PineryIUSValue) obj;
		if (donor == null) {
			if (other.donor != null) {
				return false;
			}
		} else if (!donor.equals(other.donor)) {
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
		return true;
	}

	@ShesmuVariable(signable = true)
	public String group_desc() {
		return group_desc;
	}

	@ShesmuVariable(signable = true)
	public String group_id() {
		return group_id;
	}

	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (donor == null ? 0 : donor.hashCode());
		result = prime * result + (group_desc == null ? 0 : group_desc.hashCode());
		result = prime * result + (group_id == null ? 0 : group_id.hashCode());
		result = prime * result + (ius == null ? 0 : ius.hashCode());
		result = prime * result + (library_design == null ? 0 : library_design.hashCode());
		result = prime * result + (library_name == null ? 0 : library_name.hashCode());
		result = prime * result + (int) (library_size ^ library_size >>> 32);
		result = prime * result + (library_type == null ? 0 : library_type.hashCode());
		result = prime * result + (path == null ? 0 : path.hashCode());
		result = prime * result + (project == null ? 0 : project.hashCode());
		result = prime * result + (targeted_resequencing == null ? 0 : targeted_resequencing.hashCode());
		result = prime * result + (timestamp == null ? 0 : timestamp.hashCode());
		result = prime * result + (tissue_origin == null ? 0 : tissue_origin.hashCode());
		result = prime * result + (tissue_prep == null ? 0 : tissue_prep.hashCode());
		result = prime * result + (tissue_region == null ? 0 : tissue_region.hashCode());
		result = prime * result + (tissue_type == null ? 0 : tissue_type.hashCode());
		return result;
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

	@ShesmuVariable(type = "t3sss")
	public Tuple lims() {
		return lims;
	}

	@ShesmuVariable
	public String path() {
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
