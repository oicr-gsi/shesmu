package ca.on.oicr.gsi.shesmu;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.input.ShesmuVariable;

public class TestValue {
	private final String accession;
	private final long file_size;
	private final long library_size;
	private final String path;
	private final String project;
	private final Instant timestamp;
	private final String workflow;
	private final Tuple workflow_version;
	private final Set<String> stuff = new TreeSet<>();

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
		stuff.add(accession);
		stuff.add(workflow);
		stuff.add(project);
	}

	@ShesmuVariable(type = "as")
	public Set<String> stuff() {
		return stuff;
	}

	@ShesmuVariable(type = "s")
	public String accession() {
		return accession;
	}

	@ShesmuVariable(type = "i")
	public long file_size() {
		return file_size;
	}

	@ShesmuVariable(type = "i", signable = true)
	public long library_size() {
		return library_size;
	}

	@ShesmuVariable(type = "s")
	public String path() {
		return path;
	}

	@ShesmuVariable(type = "s", signable = true)
	public String project() {
		return project;
	}

	@ShesmuVariable(type = "d")
	public Instant timestamp() {
		return timestamp;
	}

	@ShesmuVariable(type = "s")
	public String workflow() {
		return workflow;
	}

	@ShesmuVariable(type = "t3iii")
	public Tuple workflow_version() {
		return workflow_version;
	}

}
