package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import java.util.stream.Stream;

public interface OliveCompilerServices extends ExpressionCompilerServices, ConstantRetriever {
  ActionDefinition action(String name);

  boolean addMetric(String metricName);

  InputFormatDefinition inputFormat(String format);

  CallableDefinition olive(String name);

  RefillerDefinition refiller(String name);

  Stream<SignatureDefinition> signatures();

  DumperDefinition upsertDumper(String dumper);
}
