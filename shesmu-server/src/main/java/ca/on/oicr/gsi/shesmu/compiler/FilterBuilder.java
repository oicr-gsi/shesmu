package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_STREAM_TYPE;
import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.METHOD_STREAM__FILTER;
import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.METHOD_STREAM__ON_CLOSE;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

public interface FilterBuilder {
  FilterBuilder SIMPLE =
      (line, column, rootBuilder, renderer, streamType) -> {
        LambdaBuilder.pushVirtual(
            renderer,
            RegroupVariablesBuilder.METHOD_IS_OK.getName(),
            LambdaBuilder.predicate(streamType));
        renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
      };

  static FilterBuilder of(List<RejectNode> rejectHandlers) {
    if (rejectHandlers.isEmpty()) {
      return SIMPLE;
    }
    final var freeVariables = new TreeSet<String>();
    for (final var handler : rejectHandlers) {
      handler.collectFreeVariables(freeVariables);
    }
    return (line, column, rootBuilder, renderer, streamType) -> {
      final var captures =
          rejectHandlers.stream()
              .flatMap(handler -> handler.requiredCaptures(rootBuilder))
              .toArray(LoadableValue[]::new);
      final var filterBuilder =
          new LambdaBuilder(
              renderer.root(),
              String.format("Okay? %d:%d 🔍", line, column),
              LambdaBuilder.predicate(streamType),
              Stream.concat(
                      Stream.of(captures),
                      renderer.allValues().filter(v -> freeVariables.contains(v.name())))
                  .toArray(LoadableValue[]::new));

      final var filterRenderer = filterBuilder.renderer(streamType, null);
      filterRenderer.methodGen().visitCode();
      filterRenderer.loadStream();
      filterRenderer.methodGen().invokeVirtual(streamType, RegroupVariablesBuilder.METHOD_IS_OK);
      final var failPath = filterRenderer.methodGen().newLabel();
      filterRenderer.methodGen().ifZCmp(GeneratorAdapter.EQ, failPath);
      filterRenderer.methodGen().push(true);
      filterRenderer.methodGen().returnValue();
      filterRenderer.methodGen().mark(failPath);
      rejectHandlers.forEach(handler -> handler.render(rootBuilder, filterRenderer));
      filterRenderer.methodGen().push(false);
      filterRenderer.methodGen().returnValue();
      filterRenderer.methodGen().endMethod();

      filterBuilder.push(renderer);
      renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);

      final var closeBuilder =
          new LambdaBuilder(
              renderer.root(),
              String.format("Okay? %d:%d 🗑️", line, column),
              LambdaBuilder.RUNNABLE,
              captures);
      final var closeRenderer = closeBuilder.renderer();
      closeRenderer.methodGen().visitCode();
      rejectHandlers.forEach(handler -> handler.renderOnClose(closeRenderer));
      closeRenderer.methodGen().visitInsn(Opcodes.RETURN);
      closeRenderer.methodGen().visitMaxs(0, 0);
      closeRenderer.methodGen().visitEnd();

      closeBuilder.push(renderer);
      renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__ON_CLOSE);
      renderer.methodGen().checkCast(A_STREAM_TYPE);
    };
  }

  void pushFilterMethod(
      int line, int column, RootBuilder rootBuilder, Renderer renderer, Type streamType);
}
