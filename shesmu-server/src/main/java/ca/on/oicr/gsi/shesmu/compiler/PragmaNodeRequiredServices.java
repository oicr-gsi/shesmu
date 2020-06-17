package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class PragmaNodeRequiredServices extends PragmaNode {
  private static final Type A_OLIVE_SERVICES = Type.getType(OliveServices.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method METHOD_OLIVE_SERVICES__IS_OVERLOADED_ARRAY =
      new Method("isOverloaded", Type.BOOLEAN_TYPE, new Type[] {Type.getType(String[].class)});

  private final List<String> services;

  public PragmaNodeRequiredServices(List<String> services) {
    super();
    this.services = services;
  }

  @Override
  public Stream<ImportRewriter> imports() {
    return Stream.empty();
  }

  @Override
  public void renderAtExit(RootBuilder builder) {
    // do nothing
  }

  @Override
  public void renderGuard(RootBuilder builder) {
    builder.addGuard(
        methodGen -> {
          methodGen.loadArg(0);
          methodGen.push(services.size());
          methodGen.newArray(A_STRING_TYPE);
          for (int i = 0; i < services.size(); i++) {
            methodGen.dup();
            methodGen.push(i);
            methodGen.push(services.get(i));
            methodGen.arrayStore(A_STRING_TYPE);
          }
          methodGen.invokeInterface(A_OLIVE_SERVICES, METHOD_OLIVE_SERVICES__IS_OVERLOADED_ARRAY);
        });
  }

  @Override
  public void timeout(AtomicInteger timeout) {
    // do nothing
  }
}
