package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import java.util.function.Consumer;
import org.objectweb.asm.Type;

/** A definition for a parameter that should be user-definable for an action */
public interface ActionParameterDefinition {
  /**
   * Create a parameter definition that will be written to a public field
   *
   * @param fieldName the name of the field
   * @param fieldType type of the field
   * @param required whether this field must be set in the script
   */
  public static ActionParameterDefinition forField(
      String name, String fieldName, Imyhat fieldType, boolean required) {
    return new ActionParameterDefinition() {

      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean required() {
        return required;
      }

      @Override
      public void store(
          Renderer renderer, Type owner, int actionLocal, Consumer<Renderer> loadParameter) {
        renderer.methodGen().loadLocal(actionLocal);
        loadParameter.accept(renderer);
        renderer.methodGen().putField(owner, fieldName, fieldType.asmType());
      }

      @Override
      public Imyhat type() {
        return fieldType;
      }
    };
  }

  /** The name of the parameter as the user will set it. */
  String name();

  /**
   * Whether this parameter is required or not.
   *
   * <p>If not required, the user may omit setting the value.s
   */
  boolean required();

  /**
   * A procedure to write the bytecode to set the parameter in the action instance
   *
   * @param renderer The method where the code is being generated
   * @param owner The type of the action being generated
   * @param actionLocal The local variable holding the action being populated
   * @param loadParameter a callback to load the desired value for the parameter; it should be
   *     called exactly once.
   */
  void store(Renderer renderer, Type owner, int actionLocal, Consumer<Renderer> loadParameter);

  /** The type of the parameter */
  Imyhat type();
}
