package ca.on.oicr.gsi.shesmu.core.refillers;

import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.RefillerParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.refill.RefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor;
import ca.on.oicr.gsi.shesmu.server.plugins.CallSiteRegistry;
import ca.on.oicr.gsi.status.ConfigurationSection;
import java.io.PrintStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public final class PurgeRefiller<T> extends Refiller<T> {
  private static final CallSiteRegistry<Integer> ACTION_PROCESSOR_REGISTRY =
      new CallSiteRegistry<>();
  public static final Type A_ACTION_PROCESSOR_TYPE = Type.getType(ActionProcessor.class);
  public static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  public static final Type A_PURGE_REFILLER_TYPE = Type.getType(PurgeRefiller.class);
  private static final Handle HANDLE_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          A_PURGE_REFILLER_TYPE.getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.INT_TYPE),
          false);
  private static final AtomicInteger ID_GENERATOR = new AtomicInteger();
  public static final Method METHOD_PURGE_REFILLER__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_ACTION_PROCESSOR_TYPE});

  public static CallSite bootstrap(
      Lookup lookup, String variableName, MethodType methodType, int id) {
    return ACTION_PROCESSOR_REGISTRY.get(id);
  }

  public static DefinitionRepository of(ActionProcessor processor) {
    final var id = ID_GENERATOR.incrementAndGet();
    ACTION_PROCESSOR_REGISTRY.upsert(id, MethodHandles.constant(ActionProcessor.class, processor));
    return new DefinitionRepository() {
      @Override
      public Stream<ActionDefinition> actions() {
        return Stream.empty();
      }

      @Override
      public Stream<ConstantDefinition> constants() {
        return Stream.empty();
      }

      @Override
      public Stream<FunctionDefinition> functions() {
        return Stream.empty();
      }

      @Override
      public Stream<ConfigurationSection> listConfiguration() {
        return Stream.empty();
      }

      @Override
      public Stream<CallableOliveDefinition> oliveDefinitions() {
        return Stream.empty();
      }

      @Override
      public Stream<RefillerDefinition> refillers() {
        return Stream.of(
            new RefillerDefinition() {
              @Override
              public String description() {
                return "Purges actions from Shesmu by action identifier.";
              }

              @Override
              public Path filename() {
                return null;
              }

              @Override
              public String name() {
                return "std::shesmu::purge";
              }

              @Override
              public Stream<RefillerParameterDefinition> parameters() {
                return Stream.of(
                    new RefillerParameterDefinition() {
                      @Override
                      public String name() {
                        return "action_id";
                      }

                      @Override
                      public void render(Renderer renderer, int refillerLocal, int functionLocal) {
                        renderer.methodGen().loadLocal(refillerLocal);
                        renderer.methodGen().loadLocal(functionLocal);
                        renderer
                            .methodGen()
                            .putField(A_PURGE_REFILLER_TYPE, "action", A_FUNCTION_TYPE);
                      }

                      @Override
                      public Imyhat type() {
                        return Imyhat.STRING;
                      }
                    });
              }

              @Override
              public void render(Renderer renderer) {
                renderer.methodGen().newInstance(A_PURGE_REFILLER_TYPE);
                renderer.methodGen().dup();
                renderer
                    .methodGen()
                    .invokeDynamic(
                        "fetch", Type.getMethodDescriptor(A_ACTION_PROCESSOR_TYPE), HANDLE_BSM, id);
                renderer
                    .methodGen()
                    .invokeConstructor(A_PURGE_REFILLER_TYPE, METHOD_PURGE_REFILLER__CTOR);
              }
            });
      }

      @Override
      public Stream<SignatureDefinition> signatures() {
        return Stream.empty();
      }

      @Override
      public void writeJavaScriptRenderer(PrintStream writer) {
        // No rendering required.
      }
    };
  }

  @RefillerParameter public Function<T, String> action;
  private final ActionProcessor processor;

  public PurgeRefiller(ActionProcessor processor) {
    this.processor = processor;
  }

  @Override
  public void consume(Stream<T> items) {
    processor.purge(processor.ids(items.map(action).distinct().toList()));
  }
}
