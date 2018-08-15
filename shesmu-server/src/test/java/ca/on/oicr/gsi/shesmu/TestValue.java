package ca.on.oicr.gsi.shesmu;

public class TestValue {

	private final long l;
	private final String s;

	public TestValue(long l, String s) {
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
