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

	public abstract String name();

	public abstract Imyhat type();
}
