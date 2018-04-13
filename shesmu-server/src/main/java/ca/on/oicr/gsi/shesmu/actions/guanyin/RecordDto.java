package ca.on.oicr.gsi.shesmu.actions.guanyin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bean of ecord responses from Guanyin
 * 
 * This does not map all fields: only the ones actually consume by Shesmu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordDto {
	private long id;
	private long report;
	private String generated;

	@JsonProperty("report_record_id")
	public long getId() {
		return id;
	}

	@JsonProperty("report_id")
	public long getReport() {
		return report;
	}

	public void setId(long id) {
		this.id = id;
	}

	public void setReport(long report) {
		this.report = report;
	}

	@JsonProperty("date_generated")
	public String getGenerated() {
		return generated;
	}

	public void setGenerated(String generated) {
		this.generated = generated;
	}

}
