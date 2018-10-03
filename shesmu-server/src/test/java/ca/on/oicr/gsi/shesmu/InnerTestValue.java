package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.util.input.Export;

public class InnerTestValue {

	private final long l;
	private final String s;

	public InnerTestValue(long l, String s) {
		super();
		this.l = l;
		this.s = s;
	}

	@Export(type = "i")
	public long l() {
		return l;
	}

	@Export(type = "s")
	public String s() {
		return s;
	}

}
