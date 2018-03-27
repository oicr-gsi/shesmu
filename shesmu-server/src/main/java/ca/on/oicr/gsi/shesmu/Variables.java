package ca.on.oicr.gsi.shesmu;

import java.time.Instant;

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
	private final Tuple workflow_version;

	public Variables(String accession, String path, String metatype, String md5, long file_size, String workflow,
			Tuple workflow_version, String project, String library_name, String donor, Tuple ius, String library_design,
			String tissue_type, String tissue_origin, String tissue_prep, String targeted_resequencing,
			String tissue_region, String group_id, String group_desc, long library_size, String library_type,
			Instant timestamp, String source) {
		super();
		this.accession = accession;
		this.path = path;
		this.metatype = metatype;
		this.md5 = md5;
		this.file_size = file_size;
		this.workflow = workflow;
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

	@Export(type = "t3iii")
	public Tuple workflow_version() {
		return workflow_version;
	}
}
