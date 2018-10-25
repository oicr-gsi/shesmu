package ca.on.oicr.gsi.shesmu.nabu;

import java.time.Instant;

import ca.on.oicr.gsi.shesmu.util.input.ShesmuVariable;

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

	@ShesmuVariable
	public String comment() {
		return comment;
	}

	@ShesmuVariable
	public String filepath() {
		return filepath;
	}

	@ShesmuVariable
	public long fileswid() {
		return fileswid;
	}

	@ShesmuVariable
	public String project() {
		return project;
	}

	@ShesmuVariable
	public Instant qcdate() {
		return qcdate;
	}

	@ShesmuVariable
	public String qcstatus() {
		return qcstatus;
	}

	@ShesmuVariable
	public String username() {
		return username;
	}
}
