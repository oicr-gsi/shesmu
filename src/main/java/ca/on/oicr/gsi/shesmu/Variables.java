package ca.on.oicr.gsi.shesmu;

import java.util.Set;

public final class Variables {
	private final long accession;
	private final Set<String> donor;
	private final long file_size;
	private final Set<String> group_desc;
	private final Set<String> group_id;
	private final Tuple ius;
	private final Set<String> library_sample;
	private final Set<Long> library_size;
	private final Set<String> library_template_type;
	private final Set<String> library_type;
	private final String md5;
	private final String metatype;
	private final String path;
	private final Set<String> study;
	private final Set<String> targeted_resequencing;
	private final Set<String> tissue_origin;
	private final Set<String> tissue_prep;
	private final Set<String> tissue_region;
	private final Set<String> tissue_type;
	private final String workflow;
	private final Tuple workflow_version;

	public Variables(long accession, String path, String metatype, String md5, long file_size, String workflow,
			Tuple workflow_version, Set<String> study, Set<String> library_sample, Set<String> donor, Tuple ius,
			Set<String> library_template_type, Set<String> tissue_type, Set<String> tissue_origin,
			Set<String> tissue_prep, Set<String> targeted_resequencing, Set<String> tissue_region, Set<String> group_id,
			Set<String> group_desc, Set<Long> library_size, Set<String> library_type) {
		super();
		this.accession = accession;
		this.path = path;
		this.metatype = metatype;
		this.md5 = md5;
		this.file_size = file_size;
		this.workflow = workflow;
		this.workflow_version = workflow_version;
		this.study = study;
		this.library_sample = library_sample;
		this.donor = donor;
		this.ius = ius;
		this.library_template_type = library_template_type;
		this.tissue_type = tissue_type;
		this.tissue_origin = tissue_origin;
		this.tissue_prep = tissue_prep;
		this.targeted_resequencing = targeted_resequencing;
		this.tissue_region = tissue_region;
		this.group_id = group_id;
		this.group_desc = group_desc;
		this.library_size = library_size;
		this.library_type = library_type;
	}

	public long accession() {
		return accession;
	}

	public Set<String> donor() {
		return donor;
	}

	public long file_size() {
		return file_size;
	}

	public Set<String> group_desc() {
		return group_desc;
	}

	public Set<String> group_id() {
		return group_id;
	}

	public Tuple ius() {
		return ius;
	}

	public Set<String> library_sample() {
		return library_sample;
	}

	public Set<Long> library_size() {
		return library_size;
	}

	public Set<String> library_template_type() {
		return library_template_type;
	}

	public Set<String> library_type() {
		return library_type;
	}

	public String md5() {
		return md5;
	}

	public String metatype() {
		return metatype;
	}

	public String path() {
		return path;
	}

	public Set<String> study() {
		return study;
	}

	public Set<String> targeted_resequencing() {
		return targeted_resequencing;
	}

	public Set<String> tissue_origin() {
		return tissue_origin;
	}

	public Set<String> tissue_prep() {
		return tissue_prep;
	}

	public Set<String> tissue_region() {
		return tissue_region;
	}

	public Set<String> tissue_type() {
		return tissue_type;
	}

	public String workflow() {
		return workflow;
	}

	public Tuple workflow_version() {
		return workflow_version;
	}
}
