package ca.on.oicr.gsi.shesmu.util.definitions;

import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Consumer;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * A parameter definition for a parameter in a JSON object
 *
 * <p>This assumes that the {@link Action} implements {@link JsonParameterised} and will set a
 * property in that JSON object.
 *
 * @author amasella
 */
public final class JsonParameter implements ActionParameterDefinition {
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_JSON_PARAMETERISED_TYPE = Type.getType(JsonParameterised.class);
  private static final Type A_OBJECT_NODE_TYPE = Type.getType(ObjectNode.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method METHOD_IMYHAT__PACK_JSON =
      new Method(
          "packJson",
          Type.VOID_TYPE,
          new Type[] {A_OBJECT_NODE_TYPE, A_STRING_TYPE, A_OBJECT_TYPE});
  private static final Method METHOD_JSON_PARAMETERISED__PARAMETERS =
      new Method("parameters", A_OBJECT_NODE_TYPE, new Type[] {});

  private final String name;
  private final boolean required;
  private final Imyhat type;

  /**
   * The name of the JSON field
   *
   * @param name the JSON property name
   * @param type the type to use
   * @param required whether this parameter is required
   */
  public JsonParameter(String name, Imyhat type, boolean required) {
    this.name = name;
    this.type = type;
    this.required = required;
  }

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
    renderer.loadImyhat(type.descriptor());
    renderer.methodGen().loadLocal(actionLocal);
    renderer
        .methodGen()
        .invokeInterface(A_JSON_PARAMETERISED_TYPE, METHOD_JSON_PARAMETERISED__PARAMETERS);
    renderer.methodGen().push(name);
    loadParameter.accept(renderer);
    renderer.methodGen().box(type.asmType());
    renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__PACK_JSON);
  }

  @Override
  public Imyhat type() {
    return type;
  }
}
