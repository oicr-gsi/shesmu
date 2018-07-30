package ca.on.oicr.gsi.shesmu;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class Response {
	private ObjectNode[] results;
	private long total;

	public ObjectNode[] getResults() {
		return results;
	}

	public long getTotal() {
		return total;
	}

	public void setResults(ObjectNode[] results) {
		this.results = results;
	}

	public void setTotal(long total) {
		this.total = total;
	}
}
