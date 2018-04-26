package ca.on.oicr.gsi.shesmu.jira;

public final class Configuration {
	private String projectKey;
	private String token;
	private String url;

	public String getProjectKey() {
		return projectKey;
	}

	public String getToken() {
		return token;
	}

	public String getUrl() {
		return url;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}