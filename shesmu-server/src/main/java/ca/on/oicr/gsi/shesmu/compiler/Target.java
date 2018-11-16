package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * A defined variable in a program
 */
public interface Target {
	public enum Flavour {
		CONSTANT(false), LAMBDA(false), PARAMETER(false), STREAM(true), STREAM_SIGNABLE(true), STREAM_SIGNATURE(true);
		private final boolean isStream;

		private Flavour(boolean isStream) {
			this.isStream = isStream;
		}

		public boolean isStream() {
			return isStream;
		}

		public boolean needsCapture() {
			return !isStream;
		}
	}

	public static final Target BAD = new Target() {

		@Override
		public Flavour flavour() {
			return Flavour.CONSTANT;
		}

		@Override
		public String name() {
			return "<BAD>";
		}

		@Override
		public Imyhat type() {
			return Imyhat.BAD;
		}
	};

	/**
	 * What category of variables this one belongs to
	 */
	Flavour flavour();

	/**
	 * The Shemsu name for this variable
	 */
	String name();

	/**
	 * The Shesmu type for this variable
	 */
	Imyhat type();
}
