package ca.on.oicr.gsi.shesmu.input.nabu;

import java.time.Instant;
import java.util.Set;

import ca.on.oicr.gsi.shesmu.Export;

public class NabuValue {
	private final String comment;
	private final String filepath;
	private final long fileswid;
	private final String project;
	private final Instant qcdate;
	private final String qcstatus;
	private final boolean skip;
	private final String stalestatus;
	private final Set<Long> upstream;
	private final String username;

	public NabuValue(long fileswid, String filepath, String qcstatus, String username, String comment, String project,
			String stalestatus, Instant qcdate, Set<Long> upstream, boolean skip) {
		super();
		this.fileswid = fileswid;
		this.filepath = filepath;
		this.qcstatus = qcstatus;
		this.username = username;
		this.comment = comment;
		this.project = project;
		this.stalestatus = stalestatus;
		this.qcdate = qcdate;
		this.upstream = upstream;
		this.skip = skip;
	}

	@Export(type = "s")
	public String comment() {
		return comment;
	}

	@Export(type = "s")
	public String filepath() {
		return filepath;
	}

	@Export(type = "i")
	public long fileswid() {
		return fileswid;
	}

	@Export(type = "s")
	public String project() {
		return project;
	}

	@Export(type = "d")
	public Instant qcdate() {
		return qcdate;
	}

	@Export(type = "s")
	public String qcstatus() {
		return qcstatus;
	}

	@Export(type = "b")
	public boolean skip() {
		return skip;
	}

	@Export(type = "s")
	public String stalestatus() {
		return stalestatus;
	}

	@Export(type = "ai")
	public Set<Long> upstream() {
		return upstream;
	}

	@Export(type = "s")
	public String username() {
		return username;
	}
}
