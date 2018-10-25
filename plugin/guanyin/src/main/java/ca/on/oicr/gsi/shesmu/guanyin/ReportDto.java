package ca.on.oicr.gsi.shesmu.guanyin;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import ca.on.oicr.gsi.shesmu.compiler.Parser;

/**
 * Bean of Report responses from Guanyin
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportDto {
	private String category;
	private long id;
	private String name;
	private Map<String, ParameterInfo> permittedParameters;
	private String version;

	public String getCategory() {
		return category;
	}

	@JsonProperty("report_id")
	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	@JsonProperty("permitted_parameters")
	public Map<String, ParameterInfo> getPermittedParameters() {
		return permittedParameters;
	}

	public String getVersion() {
		return version;
	}

	public boolean isValid() {
		return Parser.IDENTIFIER.matcher(category).matches() && Parser.IDENTIFIER.matcher(name).matches();
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public void setId(long id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPermittedParameters(Map<String, ParameterInfo> permittedParameters) {
		this.permittedParameters = permittedParameters;
	}

	public void setVersion(String version) {
		this.version = version;
	}
}
