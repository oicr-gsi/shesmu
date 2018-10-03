package ca.on.oicr.gsi.shesmu.compiler.description;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.Imyhat;

public class VariableInformation {

	public enum Behaviour {
		DEFINITION, OBSERVER, PASSTHROUGH
	}

	private final Behaviour behaviour;
	private final Set<String> inputs;
	private final String name;
	private final Imyhat type;

	public VariableInformation(String name, Imyhat type, Stream<String> inputs, Behaviour behaviour) {
		this.name = name;
		this.type = type;
		this.inputs = inputs.collect(Collectors.toCollection(TreeSet::new));
		this.behaviour = behaviour;
	}

	public Behaviour behaviour() {
		return behaviour;
	}

	public Stream<String> inputs() {
		return inputs.stream();
	}

	public String name() {
		return name;
	}

	public Imyhat type() {
		return type;
	}

}
