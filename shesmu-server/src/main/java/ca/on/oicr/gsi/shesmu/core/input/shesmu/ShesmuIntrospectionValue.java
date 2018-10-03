package ca.on.oicr.gsi.shesmu.core.input.shesmu;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.SourceLocation;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.input.Export;

public class ShesmuIntrospectionValue {
	private final Action action;
	private final Instant changed;
	private final Instant checked;
	private final Instant generated;
	private final Set<Tuple> locations;
	private final ActionState state;

	public ShesmuIntrospectionValue(Action action, Instant changed, Instant checked, Instant generated, ActionState state,
			Set<SourceLocation> locations) {
		super();
		this.action = action;
		this.changed = changed;
		this.checked = checked;
		this.generated = generated;
		this.state = state;
		this.locations = locations.stream()
				.map(l -> new Tuple(l.fileName(), Long.valueOf(l.line()), Long.valueOf(l.column()), l.time()))
				.collect(Collectors.toSet());
	}

	@Export(type = "d")
	public Instant changed() {
		return changed;
	}

	@Export(type = "d")
	public Instant checked() {
		return checked;
	}

	@Export(type = "d")
	public Instant generated() {
		return generated;
	}

	@Export(type = "at4siid")
	public Set<Tuple> locations() {
		return locations;
	}

	@Export(type = "i")
	public long priority() {
		return action.priority();
	}

	@Export(type = "i")
	public long retry() {
		return action.retryMinutes();
	}

	@Export(type = "s")
	public String state() {
		return state.name();
	}

	@Export(type = "s")
	public String type() {
		return action.type();
	}

}
