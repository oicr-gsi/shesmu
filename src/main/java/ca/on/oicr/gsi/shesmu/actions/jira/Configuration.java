package ca.on.oicr.gsi.shesmu.actions.jira;

public final class Configuration {
	private String name;
	private String password;
	private String projectKey;
	private String url;
	private String user;

	public String getName() {
		return name;
	}

	public String getPassword() {
		return password;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public String getUrl() {
		return url;
	}

	public String getUser() {
		return user;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setUser(String user) {
		this.user = user;
	}
}