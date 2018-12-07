package ca.on.oicr.gsi.shesmu.pinery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PineryConfiguration {
	private String provider;
	private String url;

	public String getProvider() {
		return provider;
	}

	public String getUrl() {
		return url;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
