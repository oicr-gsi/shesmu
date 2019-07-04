package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.util.LoadedConfiguration;
import ca.on.oicr.gsi.status.ConfigurationSection;
import java.io.PrintStream;
import java.util.stream.Stream;

/** A service class that can provide external constants that should be visible to Shesmu programs */
public interface DefinitionRepository extends LoadedConfiguration {

  static DefinitionRepository concat(DefinitionRepository... definitionRepositories) {
    return new DefinitionRepository() {

      @Override
      public Stream<ActionDefinition> actions() {
        return Stream.of(definitionRepositories).flatMap(DefinitionRepository::actions);
      }

      @Override
      public Stream<ConstantDefinition> constants() {
        return Stream.of(definitionRepositories).flatMap(DefinitionRepository::constants);
      }

      @Override
      public Stream<RefillerDefinition> refillers() {
        return Stream.of(definitionRepositories).flatMap(DefinitionRepository::refillers);
      }

      @Override
      public Stream<FunctionDefinition> functions() {
        return Stream.of(definitionRepositories).flatMap(DefinitionRepository::functions);
      }

      @Override
      public Stream<ConfigurationSection> listConfiguration() {
        return Stream.of(definitionRepositories).flatMap(DefinitionRepository::listConfiguration);
      }

      @Override
      public Stream<SignatureDefinition> signatures() {
        return Stream.of(definitionRepositories).flatMap(DefinitionRepository::signatures);
      }

      @Override
      public void writeJavaScriptRenderer(PrintStream writer) {
        for (DefinitionRepository repository : definitionRepositories) {
          repository.writeJavaScriptRenderer(writer);
        }
      }
    };
  }

  /**
   * Get the known actions
   *
   * <p>This can be updated over time.
   *
   * @return a stream of plugins for actions the compiler can use
   */
  Stream<ActionDefinition> actions();

  /** Provide all constants know by this service */
  Stream<ConstantDefinition> constants();

  Stream<RefillerDefinition> refillers();
  /**
   * Query the repository
   *
   * @return a stream functions
   */
  Stream<FunctionDefinition> functions();

  Stream<SignatureDefinition> signatures();

  /** Write all the JavaScript code needed to pretty print this action. */
  void writeJavaScriptRenderer(PrintStream writer);
}
