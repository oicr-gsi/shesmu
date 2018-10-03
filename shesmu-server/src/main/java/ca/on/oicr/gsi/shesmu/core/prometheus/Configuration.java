package ca.on.oicr.gsi.shesmu.core.prometheus;

/**
 * Bean of the on-disk configuration for a {@link PrometheusThrottler}.
 */
public class Configuration {
	private String alertmanager;
	private String environment;

	public String getAlertmanager() {
		return alertmanager;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setAlertmanager(String alertmanager) {
		this.alertmanager = alertmanager;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

}
