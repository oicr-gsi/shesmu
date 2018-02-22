package ca.on.oicr.gsi.shesmu.actions.guanyin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultDto {
	private long report_id;
	private long report_record_id;

	public long getId() {
		return report_record_id;
	}

	public long getReport() {
		return report_id;
	}

	public void setId(long report_record_id) {
		this.report_record_id = report_record_id;
	}

	public void setReport(long report_id) {
		this.report_id = report_id;
	}

}
