package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * A defined variable in a program
 */
public abstract class Target {
	public enum Flavour {
		CONSTANT, LAMBDA, PARAMETER, STREAM
	}

	/**
	 * What category of variables this one belongs to
	 */
	public abstract Flavour flavour();

	/**
	 * The Shemsu name for this variable
	 */
	public abstract String name();

	/**
	 * The Shesmu type for this variable
	 */
	public abstract Imyhat type();
}
