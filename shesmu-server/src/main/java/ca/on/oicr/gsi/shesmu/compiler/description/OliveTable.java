package ca.on.oicr.gsi.shesmu.compiler.description;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OliveTable {
  private final int column;
  private final String description;
  private final int line;
  private final boolean producesActions;
  private final List<OliveClauseRow> rows;
  private final String syntax;
  private final Set<String> tags;
  private final List<VariableInformation> variables;

  public OliveTable(
      String syntax,
      int line,
      int column,
      boolean producesActions,
      Set<String> tags,
      String description,
      Stream<OliveClauseRow> rows,
      Stream<VariableInformation> variables) {
    super();
    this.syntax = syntax;
    this.line = line;
    this.column = column;
    this.producesActions = producesActions;
    this.tags = tags;
    this.description = description;
    this.rows = rows.collect(Collectors.toList());
    this.variables = variables.collect(Collectors.toList());
  }

  public Stream<OliveClauseRow> clauses() {
    return rows.stream();
  }

  public int column() {
    return column;
  }

  public String description() {
    return description;
  }

  public int line() {
    return line;
  }

  public boolean producesActions() {
    return producesActions;
  }

  public String syntax() {
    return syntax;
  }

  public Stream<String> tags() {
    return tags.stream();
  }

  public Stream<VariableInformation> variables() {
    return variables.stream();
  }
}
