package ca.on.oicr.gsi.shesmu.actions.guanyin;

import com.fasterxml.jackson.databind.node.ObjectNode;

class ReportDto {
	private String category;
	private String name;
	private ObjectNode permitted_parameters;
	private long report_id;
	private String version;

	public String getCategory() {
		return category;
	}

	public long getId() {
		return report_id;
	}

	public String getName() {
		return name;
	}

	public ObjectNode getPermittedParameters() {
		return permitted_parameters;
	}

	public String getVersion() {
		return version;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public void setId(long report_id) {
		this.report_id = report_id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPermittedParameters(ObjectNode permitted_parameters) {
		this.permitted_parameters = permitted_parameters;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public ReportDefinition toDefinition(String guanyinUrl, String drmaaUrl) {
		// TODO Auto-generated method stub
		return null;
	}

}
