package ca.on.oicr.gsi.shesmu.actions.guanyin;

public class Configuration {
	private String drmaa;
	private String drmaaPsk;
	private String guanyin;
	private String rootDirectory;

	public String getDrmaa() {
		return drmaa;
	}

	public String getDrmaaPsk() {
		return drmaaPsk;
	}

	public String getGuanyin() {
		return guanyin;
	}

	public String getRootDirectory() {
		return rootDirectory;
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

	public void setRootDirectory(String rootDirectory) {
		this.rootDirectory = rootDirectory;
	}
}
