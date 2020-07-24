package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_INPUT_PROVIDER_TYPE;
import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.METHOD_INPUT_PROVIDER__FETCH;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class PragmaNodeInputGuard extends PragmaNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Method METHOD_EVERYTHING =
      new Method(
          "everything",
          A_OBJECT_TYPE,
          new Type[] {
            Type.getType(Stream.class), Type.getType(BiConsumer.class), Type.getType(Supplier.class)
          });
  private final int line;
  private final int column;
  private final String input;
  private final GroupNode collector;
  private final ExpressionNode check;
  InputFormatDefinition inputFormatDefinition;

  public PragmaNodeInputGuard(
      int line, int column, String input, GroupNode collector, ExpressionNode check) {
    this.line = line;
    this.column = column;
    this.input = input;
    this.collector = collector;
    this.check = check;
  }

  @Override
  public boolean check(OliveCompilerServices services, Consumer<String> errorHandler) {
    inputFormatDefinition = services.inputFormat(input);
    if (inputFormatDefinition == null) {
      errorHandler.accept(String.format("%d:%d: Unknown input format “%s”.", line, column, input));
      return false;
    }
    final NameDefinitions outerDefs =
        new NameDefinitions(
            services.constants(false).collect(Collectors.toMap(Target::name, Function.identity())),
            true);
    final NameDefinitions collectorDefs =
        NameDefinitions.root(inputFormatDefinition, Stream.empty(), services.signatures());
    final NameDefinitions checkDefs = outerDefs.bind(collector);
    if (check.resolveDefinitions(services, errorHandler)
        && collector.resolveDefinitions(services, errorHandler)
        && collector.resolve(collectorDefs, outerDefs, errorHandler)
        && check.resolve(checkDefs, errorHandler)
        && collector.typeCheck(errorHandler)
        && check.typeCheck(errorHandler)) {
      if (check.type().isSame(Imyhat.BOOLEAN)) {
        return true;
      }
      check.typeError(Imyhat.BOOLEAN, check.type(), errorHandler);
    }
    return false;
  }

  @Override
  public Stream<ImportRewriter> imports() {
    return Stream.empty();
  }

  @Override
  public void renderAtExit(RootBuilder root) {
    // Do nothing.
  }

  @Override
  public void renderGuard(RootBuilder root) {
    final Set<String> signableNames = new HashSet<>();
    collector.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
    final Set<String> freeVariables = new HashSet<>();
    collector.collectFreeVariables(freeVariables, Flavour::needsCapture);

    final String className = String.format("shesmu/dyn/Guard_%d_%d", line, column);

    final Type newType = Type.getObjectType(className);
    final LoadableValue[] capturedVariables =
        root.constants(false)
            .filter(c -> freeVariables.contains(c.name()))
            .toArray(LoadableValue[]::new);

    final LambdaBuilder newLambda =
        new LambdaBuilder(
            root,
            String.format("Group %d:%d ✨", line, column),
            LambdaBuilder.supplier(newType),
            capturedVariables);
    final LambdaBuilder collectLambda =
        new LambdaBuilder(
            root,
            String.format("Group %d:%d 🧲", line, column),
            LambdaBuilder.biconsumer(newType, A_OBJECT_TYPE),
            capturedVariables);

    final List<Target> signables =
        inputFormatDefinition
            .baseStreamVariables()
            .filter(t -> t.flavour() == Flavour.STREAM_SIGNABLE && signableNames.contains(t.name()))
            .collect(Collectors.toList());
    final String prefix = String.format("Guard %d:%d ", line, column);
    root.signatureVariables()
        .forEach(
            signer ->
                BaseOliveBuilder.createSignatureInfrastructure(
                    root, prefix, inputFormatDefinition, signables, signer));

    final Renderer newRenderer =
        newLambda.renderer(
            A_OBJECT_TYPE,
            (signatureDefinition, renderer) ->
                OliveBuilder.renderSigner(
                    root, inputFormatDefinition, prefix, signatureDefinition, renderer));
    final Renderer collectedRenderer =
        collectLambda.renderer(
            A_OBJECT_TYPE,
            1,
            (signatureDefinition, renderer) ->
                OliveBuilder.renderSigner(
                    root, inputFormatDefinition, prefix, signatureDefinition, renderer));
    final RegroupVariablesBuilder regrouper =
        new RegroupVariablesBuilder(
            root, className, newRenderer, collectedRenderer, capturedVariables.length);

    collector.render(regrouper, root);
    regrouper.finish();

    root.addGuard(
        methodGen -> {
          final int local = methodGen.newLocal(newType);
          final Renderer runMethod =
              new RendererLocalStream(
                  root,
                  methodGen,
                  local,
                  newType,
                  root.constants(false),
                  RootBuilder::invalidSignerEmitter);
          runMethod.methodGen().loadArg(1);
          runMethod.methodGen().push(inputFormatDefinition.name());
          runMethod
              .methodGen()
              .invokeInterface(A_INPUT_PROVIDER_TYPE, METHOD_INPUT_PROVIDER__FETCH);
          collectLambda.push(runMethod);
          newLambda.push(runMethod);
          runMethod.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_EVERYTHING);
          runMethod.methodGen().unbox(newType);
          runMethod.methodGen().storeLocal(local);

          runMethod.methodGen().loadLocal(local);
          runMethod.methodGen().invokeVirtual(newType, RegroupVariablesBuilder.METHOD_IS_OK);
          final Label end = runMethod.methodGen().newLabel();
          final Label skip = runMethod.methodGen().newLabel();
          runMethod.methodGen().ifZCmp(GeneratorAdapter.EQ, skip);

          check.render(runMethod);
          runMethod.methodGen().not();
          runMethod.methodGen().goTo(end);
          runMethod.methodGen().mark(skip);
          runMethod.methodGen().push(true);
          runMethod.methodGen().mark(end);
        });
  }

  @Override
  public void timeout(AtomicInteger timeout) {
    // Do nothing.
  }
}
