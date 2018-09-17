package ca.on.oicr.gsi.shesmu.input.nabu;

import java.time.Instant;

import ca.on.oicr.gsi.shesmu.Export;

public class NabuValue {
	private final String comment;
	private final String filepath;
	private final long fileswid;
	private final String project;
	private final Instant qcdate;
	private final String qcstatus;
	private final String username;

	public NabuValue(long fileswid, String filepath, String qcstatus, String username, String comment, String project,
			Instant qcdate) {
		super();
		this.fileswid = fileswid;
		this.filepath = filepath;
		this.qcstatus = qcstatus;
		this.username = username;
		this.comment = comment;
		this.project = project;
		this.qcdate = qcdate;
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

	@Export(type = "s")
	public String username() {
		return username;
	}
}
