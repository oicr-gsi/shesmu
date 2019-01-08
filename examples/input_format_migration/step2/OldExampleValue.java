package ca.on.oicr.gsi.shesmu.example;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.input.ShesmuVariable;

public class OldExampleValue {
	private final ExampleValue backing;

	public ExampleValueOld(ExampleValue backing) {
		this.backing = backing;
	}

	@ShesmuVariable(type = "as")
	public Set<String> stuff() {
		return backing.stuff();
	}

	@ShesmuVariable(type = "s")
	public String accession() {
		return Long.toString(backing.accession());
	}

	@ShesmuVariable(type = "i")
	public long file_size() {
		return backing.file_size();
	}

	@ShesmuVariable(type = "i", signable = true)
	public long library_size() {
		return backing.library_size();
	}

	@ShesmuVariable
	public Path path() {
		return backing.path();
	}

	@ShesmuVariable(type = "s", signable = true)
	public String project() {
		return backing.project();
	}

	@ShesmuVariable(type = "d")
	public Instant timestamp() {
		return backing.timestamp();
	}

	@ShesmuVariable(type = "s")
	public String workflow() {
		return backing.workflow();
	}

	@ShesmuVariable(type = "t3iii")
	public Tuple workflow_version() {
		return backing.workflow_version();
	}

}
