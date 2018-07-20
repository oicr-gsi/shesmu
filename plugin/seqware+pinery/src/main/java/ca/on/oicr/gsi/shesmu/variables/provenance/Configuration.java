package ca.on.oicr.gsi.shesmu.variables.provenance;

public class Configuration {
	private String jar;
	private String settings;
	private WorkflowConfiguration[] workflows;

	public String getJar() {
		return jar;
	}

	public String getSettings() {
		return settings;
	}

	public WorkflowConfiguration[] getWorkflows() {
		return workflows;
	}

	public void setJar(String jar) {
		this.jar = jar;
	}

	public void setSettings(String settings) {
		this.settings = settings;
	}

	public void setWorkflows(WorkflowConfiguration[] workflows) {
		this.workflows = workflows;
	}
}
