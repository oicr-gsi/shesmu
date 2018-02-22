package ca.on.oicr.gsi.shesmu;

public final class Variables {
	private final String accession;
	private final String donor;
	private final long file_size;
	private final String group_desc;
	private final String group_id;
	private final Tuple ius;
	private final String library_sample;
	private final Long library_size;
	private final String library_template_type;
	private final String library_type;
	private final String md5;
	private final String metatype;
	private final String path;
	private final String source;
	private final String study;
	private final String targeted_resequencing;
	private final String tissue_origin;
	private final String tissue_prep;
	private final String tissue_region;
	private final String tissue_type;
	private final String workflow;
	private final Tuple workflow_version;

	public Variables(String accession, String path, String metatype, String md5, long file_size, String workflow,
			Tuple workflow_version, String study, String library_sample, String donor, Tuple ius,
			String library_template_type, String tissue_type, String tissue_origin, String tissue_prep,
			String targeted_resequencing, String tissue_region, String group_id, String group_desc, Long library_size,
			String library_type, String source) {
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
		this.source = source;
	}

	public String accession() {
		return accession;
	}

	public String donor() {
		return donor;
	}

	public long file_size() {
		return file_size;
	}

	public String group_desc() {
		return group_desc;
	}

	public String group_id() {
		return group_id;
	}

	public Tuple ius() {
		return ius;
	}

	public String library_sample() {
		return library_sample;
	}

	public Long library_size() {
		return library_size;
	}

	public String library_template_type() {
		return library_template_type;
	}

	public String library_type() {
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

	public String source() {
		return source;
	}

	public String study() {
		return study;
	}

	public String targeted_resequencing() {
		return targeted_resequencing;
	}

	public String tissue_origin() {
		return tissue_origin;
	}

	public String tissue_prep() {
		return tissue_prep;
	}

	public String tissue_region() {
		return tissue_region;
	}

	public String tissue_type() {
		return tissue_type;
	}

	public String workflow() {
		return workflow;
	}

	public Tuple workflow_version() {
		return workflow_version;
	}
}
