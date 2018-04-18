package ca.on.oicr.gsi.shesmu.actions.guanyin;

/**
 * Bean for on-disk Guanyin service configuration files
 */
public class Configuration {
	private String drmaa;
	private String drmaaPsk;
	private String guanyin;
	private String script;

	public String getDrmaa() {
		return drmaa;
	}

	public String getDrmaaPsk() {
		return drmaaPsk;
	}

	public String getGuanyin() {
		return guanyin;
	}

	public String getScript() {
		return script;
	}

	public void setDrmaa(String drmaa) {
		this.drmaa = drmaa;
	}

	public void setDrmaaPsk(String drmaaPsk) {
		this.drmaaPsk = drmaaPsk;
	}

	public void setGuanyin(String guanyin) {
		this.guanyin = guanyin;
	}

	public void setScript(String script) {
		this.script = script;
	}
}
