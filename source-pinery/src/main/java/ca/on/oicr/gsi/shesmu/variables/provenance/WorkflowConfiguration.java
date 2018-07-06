package ca.on.oicr.gsi.shesmu.variables.provenance;

public class WorkflowConfiguration {
	private long accession;
	private String name;
	private long[] previousAccessions;
	private String[] services;
	private WorkflowType type;

	public long getAccession() {
		return accession;
	}

	public String getName() {
		return name;
	}

	public long[] getPreviousAccessions() {
		return previousAccessions;
	}

	public String[] getServices() {
		return services;
	}

	public WorkflowType getType() {
		return type;
	}

	public void setAccession(long accession) {
		this.accession = accession;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPreviousAccessions(long[] previousAccessions) {
		this.previousAccessions = previousAccessions;
	}

	public void setServices(String[] services) {
		this.services = services;
	}

	public void setType(WorkflowType type) {
		this.type = type;
	}

}
