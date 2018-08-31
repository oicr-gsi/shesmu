package ca.on.oicr.gsi.shesmu;

import java.time.Instant;

public class TestValue {
	private final String accession;
	private final long file_size;
	private final long library_size;
	private final String path;
	private final String project;
	private final Instant timestamp;
	private final String workflow;
	private final Tuple workflow_version;

	public TestValue(String accession, String path, long file_size, String workflow, Tuple workflow_version,
			String project, long library_size, Instant timestamp) {
		super();
		this.accession = accession;
		this.path = path;
		this.file_size = file_size;
		this.workflow = workflow;
		this.workflow_version = workflow_version;
		this.project = project;
		this.library_size = library_size;
		this.timestamp = timestamp;
	}

	@Export(type = "s")
	public String accession() {
		return accession;
	}

	@Export(type = "i")
	public long file_size() {
		return file_size;
	}

	@Export(type = "i", signable = true)
	public long library_size() {
		return library_size;
	}

	@Export(type = "s")
	public String path() {
		return path;
	}

	@Export(type = "s", signable = true)
	public String project() {
		return project;
	}

	@Export(type = "d")
	public Instant timestamp() {
		return timestamp;
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
