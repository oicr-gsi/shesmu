package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;

/**
 * Definitions of all the types of workflows
 *
 * These do not map exactly to Niassa's concept of a workflow. All this code
 * cares about is whether the types of the parameters in the INI are the same.
 * Any workflows that take the same parameters can share on entry here.
 *
 */
public enum LanesType {
	CELL_RANGER("+", //
			Imyhat.tuple(//
					Imyhat.tuple(Imyhat.STRING, Imyhat.INTEGER, Imyhat.STRING), // IUS
					Imyhat.STRING, // library name
					Imyhat.tuple(Imyhat.STRING, Imyhat.STRING, Imyhat.STRING), // LIMS key
					Imyhat.DATE, // last modified
					Imyhat.STRING)// group id
	) {

		@Override
		public StringableLimsKey makeLimsKey(Object value) {
			return new CellRangerLimsKey((Tuple) value);
		}

	};
	private final String delimiter;
	private final Imyhat innerType;

	private LanesType(String delimiter, Imyhat innerType) {
		this.delimiter = delimiter;
		this.innerType = innerType;
	}

	public String delimiter() {
		return delimiter;
	}

	public Imyhat innerType() {
		return innerType;
	}

	public abstract StringableLimsKey makeLimsKey(Object value);
}
