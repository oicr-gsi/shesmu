package ca.on.oicr.gsi.shesmu.core.actions.rest;

public final class FileDefinition {
	private Definition[] definitions;
	private String url;

	public Definition[] getDefinitions() {
		return definitions;
	}

	public String getUrl() {
		return url;
	}

	public void setDefinitions(Definition[] definitions) {
		this.definitions = definitions;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
