package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.SourceLocation;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.input.ShesmuVariable;

public class ShesmuIntrospectionValue {
	private final Action action;
	private final Instant changed;
	private final Instant checked;
	private final Instant generated;
	private final Set<Tuple> locations;
	private final ActionState state;

	public ShesmuIntrospectionValue(Action action, Instant changed, Instant checked, Instant generated,
			ActionState state, Set<SourceLocation> locations) {
		super();
		this.action = action;
		this.changed = changed;
		this.checked = checked;
		this.generated = generated;
		this.state = state;
		this.locations = locations.stream().map(
				l -> new Tuple(Long.valueOf(l.column()), Paths.get(l.fileName()), Long.valueOf(l.line()), l.time()))
				.collect(Collectors.toSet());
	}

	@ShesmuVariable
	public Instant changed() {
		return changed;
	}

	@ShesmuVariable
	public Instant checked() {
		return checked;
	}

	@ShesmuVariable
	public Instant generated() {
		return generated;
	}

	@ShesmuVariable(type = "ao4column$ifile$pline$itime$d")
	public Set<Tuple> locations() {
		return locations;
	}

	@ShesmuVariable
	public long priority() {
		return action.priority();
	}

	@ShesmuVariable
	public long retry() {
		return action.retryMinutes();
	}

	@ShesmuVariable
	public String state() {
		return state.name();
	}

	@ShesmuVariable
	public String type() {
		return action.type();
	}

}
