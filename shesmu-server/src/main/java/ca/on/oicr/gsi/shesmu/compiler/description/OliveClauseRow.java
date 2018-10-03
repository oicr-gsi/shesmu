package ca.on.oicr.gsi.shesmu.compiler.description;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OliveClauseRow {
	private final int column;
	private final boolean deadly;
	private final int line;
	private final boolean measuredFlow;
	private final String syntax;
	private final List<VariableInformation> variables;

	public OliveClauseRow(String syntax, int line, int column, boolean measuredFlow, boolean deadly,
			Stream<VariableInformation> variables) {
		super();
		this.syntax = syntax;
		this.line = line;
		this.column = column;
		this.measuredFlow = measuredFlow;
		this.deadly = deadly;
		this.variables = variables.collect(Collectors.toList());
	}

	public int column() {
		return column;
	}

	public boolean deadly() {
		return deadly;
	}

	public int line() {
		return line;
	}

	public boolean measuredFlow() {
		return measuredFlow;
	}

	public String syntax() {
		return syntax;
	}

	public Stream<VariableInformation> variables() {
		return variables.stream();
	}

}
