package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;
import static org.objectweb.asm.Type.*;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.PartitionCount;
import ca.on.oicr.gsi.shesmu.runtime.UnivaluedGroupAccumulator;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Helps to build a “Group” clause and the corresponding variable class */
public final class RegroupVariablesBuilder implements Regrouper {
  private abstract class BaseComposite extends Element implements Regrouper {

    private final List<Element> elements = new ArrayList<>();
    private final String prefix;

    public BaseComposite(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public final void addCollected(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      elements.add(new Collected(valueType, prefix + fieldName, loader));
    }

    @Override
    public final void addCollected(
        Imyhat keyType,
        Imyhat valueType,
        String fieldName,
        Consumer<Renderer> keyLoader,
        Consumer<Renderer> valueLoader) {
      elements.add(new Dictionary(keyType, valueType, prefix + fieldName, keyLoader, valueLoader));
    }

    @Override
    public final void addCount(String fieldName) {
      elements.add(new Count(prefix + fieldName));
    }

    @Override
    public final void addFirst(Type fieldType, String fieldName, Consumer<Renderer> loader) {
      elements.add(new First(fieldType, prefix + fieldName, loader));
    }

    @Override
    public final void addFirst(
        Type fieldType, String fieldName, Consumer<Renderer> loader, Consumer<Renderer> initial) {
      elements.add(new FirstWithDefault(fieldType, prefix + fieldName, loader, initial));
    }

    @Override
    public final void addFlatten(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      elements.add(new Flattened(valueType, prefix + fieldName, loader));
    }

    @Override
    public void addLexicalConcat(
        String fieldName, Consumer<Renderer> loader, Consumer<Renderer> delimiterLoader) {
      elements.add(new LexicalConcat(prefix + fieldName, loader, delimiterLoader));
    }

    @Override
    public final void addMatches(String name, Match matchType, Consumer<Renderer> condition) {
      elements.add(new Matches(prefix + name, matchType, condition));
    }

    @Override
    public final Regrouper addObject(String fieldName, Stream<Pair<String, Imyhat>> fields) {
      final NamedTuple namedTuple = new NamedTuple(prefix + fieldName, fields);
      elements.add(namedTuple);
      return namedTuple;
    }

    @Override
    public final void addOnlyIf(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      elements.add(new OnlyIf(valueType, prefix + fieldName, loader));
    }

    @Override
    public final void addOptima(
        Type fieldType, String fieldName, boolean max, Consumer<Renderer> loader) {
      elements.add(new Optima(fieldType, prefix + fieldName, max, loader));
    }

    @Override
    public final void addOptima(
        Type fieldType,
        String fieldName,
        boolean max,
        Consumer<Renderer> loader,
        Consumer<Renderer> initial) {
      elements.add(new OptimaWithDefault(fieldType, prefix + fieldName, max, loader, initial));
    }

    @Override
    public final void addPartitionCount(String fieldName, Consumer<Renderer> condition) {
      elements.add(new PartitionCounter(prefix + fieldName, condition));
    }

    @Override
    public final void addUnivalued(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      elements.add(new Univalued(valueType, prefix + fieldName, loader));
    }

    @Override
    public final void addUnivalued(
        Imyhat valueType,
        String fieldName,
        Consumer<Renderer> loader,
        Consumer<Renderer> defaultValue) {
      elements.add(new UnivaluedWithDefault(valueType, prefix + fieldName, loader, defaultValue));
    }

    @Override
    public final Regrouper addWhere(Consumer<Renderer> condition) {
      final Conditional c = new Conditional(condition, prefix);
      elements.add(c);
      return c;
    }

    @Override
    public final int buildConstructor(GeneratorAdapter ctor, int index) {
      return elements
          .stream()
          .reduce(
              index,
              (i, element) -> element.buildConstructor(ctor, i),
              (a, b) -> {
                throw new UnsupportedOperationException();
              });
    }

    @Override
    public final void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      elements.forEach(element -> element.buildEquals(methodGen, otherLocal, end));
    }

    @Override
    public final void buildHashCode(GeneratorAdapter method) {
      elements.forEach(element -> element.buildHashCode(method));
    }

    protected final void buildInnerCollect() {
      elements.forEach(Element::buildCollect);
    }

    @Override
    public final Stream<Type> constructorType() {
      return elements.stream().flatMap(Element::constructorType);
    }

    @Override
    public final void failIfBad(GeneratorAdapter okMethod) {
      elements.forEach(element -> element.failIfBad(okMethod));
    }

    @Override
    public final void loadConstructorArgument() {
      elements.forEach(Element::loadConstructorArgument);
    }
  }

  private abstract class BaseList extends Element {
    private final String fieldName;
    private final Consumer<Renderer> loader;
    protected final Imyhat valueType;

    private BaseList(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      super();
      this.valueType = valueType;
      this.fieldName = fieldName;
      this.loader = loader;
      buildGetter(A_SET_TYPE, fieldName);
    }

    @Override
    public final void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer
          .methodGen()
          .invokeVirtual(self, new Method(fieldName, A_SET_TYPE, new Type[] {}));
      loader.accept(collectRenderer);
      collect();
      collectRenderer.methodGen().pop();
    }

    @Override
    public final int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      Renderer.loadImyhatInMethod(ctor, valueType.descriptor());
      ctor.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_SET);
      ctor.putField(self, fieldName, A_SET_TYPE);
      return index;
    }

    @Override
    public final void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Collections are not included in equality.
    }

    @Override
    public final void buildHashCode(GeneratorAdapter hashMethod) {
      // Collections are not included in the hash.
    }

    protected abstract void collect();

    @Override
    public final Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public final void failIfBad(GeneratorAdapter okMethod) {
      // Do nothing
    }

    @Override
    public final void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class Collected extends BaseList {

    private Collected(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      super(valueType, fieldName, loader);
    }

    @Override
    protected void collect() {
      collectRenderer.methodGen().valueOf(valueType.apply(TO_ASM));
      collectRenderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__ADD);
    }
  }

  private class Conditional extends BaseComposite {

    private final Consumer<Renderer> condition;

    public Conditional(Consumer<Renderer> condition, String prefix) {
      super(prefix);
      this.condition = condition;
    }

    @Override
    public void buildCollect() {
      final Label skip = collectRenderer.methodGen().newLabel();
      condition.accept(collectRenderer);
      collectRenderer.methodGen().ifZCmp(GeneratorAdapter.EQ, skip);
      buildInnerCollect();
      collectRenderer.methodGen().mark(skip);
    }
  }

  private class Count extends Element {

    private final String fieldName;

    public Count(String fieldName) {
      this.fieldName = fieldName;
      buildGetter(LONG_TYPE, fieldName);
    }

    @Override
    public void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().dup();
      collectRenderer.methodGen().getField(self, fieldName, LONG_TYPE);
      collectRenderer.methodGen().push(1L);
      collectRenderer.methodGen().math(GeneratorAdapter.ADD, LONG_TYPE);
      collectRenderer.methodGen().putField(self, fieldName, LONG_TYPE);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      return index;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Counts are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter method) {
      // Counts are not included in hash code.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // Counts are always okay.
    }

    @Override
    public void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class Dictionary extends Element {
    private final String fieldName;
    private final Consumer<Renderer> keyLoader;
    private final Imyhat keyType;
    private final Consumer<Renderer> valueLoader;
    private final Imyhat valueType;

    private Dictionary(
        Imyhat keyType,
        Imyhat valueType,
        String fieldName,
        Consumer<Renderer> keyLoader,
        Consumer<Renderer> valueLoader) {
      super();
      this.keyType = keyType;
      this.valueType = valueType;
      this.fieldName = fieldName;
      this.keyLoader = keyLoader;
      this.valueLoader = valueLoader;
      buildGetter(A_MAP_TYPE, fieldName);
    }

    @Override
    public final void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer
          .methodGen()
          .invokeVirtual(self, new Method(fieldName, A_MAP_TYPE, new Type[] {}));
      keyLoader.accept(collectRenderer);
      collectRenderer.methodGen().box(keyType.apply(TO_ASM));
      valueLoader.accept(collectRenderer);
      collectRenderer.methodGen().box(valueType.apply(TO_ASM));
      collectRenderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__PUT);
      collectRenderer.methodGen().pop();
    }

    @Override
    public final int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      Renderer.loadImyhatInMethod(ctor, keyType.descriptor());
      ctor.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_MAP);
      ctor.putField(self, fieldName, A_MAP_TYPE);
      return index;
    }

    @Override
    public final void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Collections are not included in equality.
    }

    @Override
    public final void buildHashCode(GeneratorAdapter hashMethod) {
      // Collections are not included in the hash.
    }

    @Override
    public final Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public final void failIfBad(GeneratorAdapter okMethod) {
      // Do nothing
    }

    @Override
    public final void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class Discriminator extends Element {
    private final String fieldName;
    private final Type fieldType;
    private final Consumer<Renderer> loader;

    public Discriminator(Type fieldType, String fieldName, Consumer<Renderer> loader) {
      super();
      this.fieldType = fieldType;
      this.fieldName = fieldName;
      this.loader = loader;
      buildGetter(fieldType, fieldName);
    }

    @Override
    public void buildCollect() {
      // No collection required
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      ctor.loadArg(index);
      ctor.putField(self, fieldName, fieldType);
      return index + 1;
    }

    @Override
    public void buildEquals(GeneratorAdapter method, int otherLocal, Label end) {
      method.loadThis();
      method.getField(self, fieldName, fieldType);
      method.loadLocal(otherLocal);
      method.getField(self, fieldName, fieldType);
      switch (fieldType.getSort()) {
        case Type.ARRAY:
        case Type.OBJECT:
          method.invokeVirtual(A_OBJECT_TYPE, METHOD_EQUALS);
          method.ifZCmp(GeneratorAdapter.EQ, end);
          break;
        default:
          method.ifCmp(fieldType, GeneratorAdapter.NE, end);
      }
    }

    @Override
    public void buildHashCode(GeneratorAdapter method) {
      method.push(31);
      method.math(GeneratorAdapter.MUL, INT_TYPE);
      method.loadThis();
      method.getField(self, fieldName, fieldType);
      switch (fieldType.getSort()) {
        case Type.ARRAY:
        case Type.OBJECT:
          method.invokeVirtual(A_OBJECT_TYPE, METHOD_HASH_CODE);
          break;
        default:
          method.cast(fieldType, INT_TYPE);
          break;
      }
      method.math(GeneratorAdapter.ADD, INT_TYPE);
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.of(fieldType);
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // Do nothing
    }

    @Override
    public void loadConstructorArgument() {
      loader.accept(newRenderer);
    }
  }

  private abstract class Element {

    public abstract void buildCollect();

    public abstract int buildConstructor(GeneratorAdapter ctor, int index);

    public abstract void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end);

    public abstract void buildHashCode(GeneratorAdapter method);

    public abstract Stream<Type> constructorType();

    public abstract void failIfBad(GeneratorAdapter okMethod);

    public abstract void loadConstructorArgument();
  }

  private class First extends Element {

    private final String fieldName;
    private final Type fieldType;
    private final Consumer<Renderer> loader;

    public First(Type fieldType, String fieldName, Consumer<Renderer> loader) {
      this.fieldType = fieldType;
      this.fieldName = fieldName;
      this.loader = loader;
      classVisitor
          .visitField(
              Opcodes.ACC_PUBLIC, fieldName + "$ok", BOOLEAN_TYPE.getDescriptor(), null, null)
          .visitEnd();
      buildGetter(fieldType, fieldName);
    }

    @Override
    public void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName + "$ok", BOOLEAN_TYPE);
      final Label skip = collectRenderer.methodGen().newLabel();
      collectRenderer.methodGen().ifZCmp(GeneratorAdapter.NE, skip);

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().push(true);
      collectRenderer.methodGen().putField(self, fieldName + "$ok", BOOLEAN_TYPE);
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      loader.accept(collectRenderer);
      collectRenderer.methodGen().putField(self, fieldName, fieldType);
      collectRenderer.methodGen().mark(skip);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      return index;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Firsts are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter method) {
      // Firsts are not included in hash code.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      okMethod.loadThis();
      okMethod.getField(self, fieldName + "$ok", BOOLEAN_TYPE);
      final Label next = okMethod.newLabel();
      okMethod.ifZCmp(GeneratorAdapter.NE, next);
      okMethod.push(false);
      okMethod.returnValue();
      okMethod.mark(next);
    }

    @Override
    public void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class FirstWithDefault extends Element {

    private final String fieldName;
    private final Type fieldType;
    private final Consumer<Renderer> initial;
    private final Consumer<Renderer> loader;

    public FirstWithDefault(
        Type fieldType, String fieldName, Consumer<Renderer> loader, Consumer<Renderer> initial) {
      this.fieldType = fieldType;
      this.fieldName = fieldName;
      this.loader = loader;
      this.initial = initial;
      classVisitor
          .visitField(
              Opcodes.ACC_PUBLIC, fieldName + "$ok", BOOLEAN_TYPE.getDescriptor(), null, null)
          .visitEnd();
      buildGetter(fieldType, fieldName);
    }

    @Override
    public void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName + "$ok", BOOLEAN_TYPE);
      final Label skip = collectRenderer.methodGen().newLabel();
      collectRenderer.methodGen().ifZCmp(GeneratorAdapter.NE, skip);

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().push(true);
      collectRenderer.methodGen().putField(self, fieldName + "$ok", BOOLEAN_TYPE);
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      loader.accept(collectRenderer);
      collectRenderer.methodGen().putField(self, fieldName, fieldType);
      collectRenderer.methodGen().mark(skip);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      ctor.loadArg(index);
      ctor.putField(self, fieldName, fieldType);
      return index + 1;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // First with default are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter method) {
      // First with are not included in hash code.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.of(fieldType);
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // First with default is always ok
    }

    @Override
    public void loadConstructorArgument() {
      initial.accept(newRenderer);
    }
  }

  private class Flattened extends BaseList {

    private Flattened(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      super(valueType, fieldName, loader);
    }

    @Override
    protected void collect() {
      collectRenderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__ADD_ALL);
    }
  }

  private class LexicalConcat extends Element {

    private final Consumer<Renderer> delimiter;
    private final String fieldName;
    private final Consumer<Renderer> loader;

    public LexicalConcat(
        String fieldName, Consumer<Renderer> loader, Consumer<Renderer> delimiter) {
      this.fieldName = fieldName;
      this.loader = loader;
      this.delimiter = delimiter;
      classVisitor
          .visitField(Opcodes.ACC_PUBLIC, fieldName, A_SET_TYPE.getDescriptor(), null, null)
          .visitEnd();
      classVisitor
          .visitField(
              Opcodes.ACC_PUBLIC, fieldName + "$delim", A_STRING_TYPE.getDescriptor(), null, null)
          .visitEnd();
      final GeneratorAdapter getMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              new Method(fieldName, A_STRING_TYPE, new Type[] {}),
              null,
              null,
              classVisitor);
      getMethod.visitCode();
      getMethod.loadThis();
      getMethod.getField(self, fieldName + "$delim", A_STRING_TYPE);
      getMethod.loadThis();
      getMethod.getField(self, fieldName, A_SET_TYPE);
      getMethod.invokeStatic(A_STRING_TYPE, METHOD_STRING__JOIN);
      getMethod.returnValue();
      getMethod.endMethod();
    }

    @Override
    public void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName, A_SET_TYPE);
      loader.accept(collectRenderer);
      collectRenderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__ADD);
      collectRenderer.methodGen().pop();
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      ctor.loadArg(index);
      ctor.putField(self, fieldName + "$delim", A_STRING_TYPE);
      ctor.loadThis();
      ctor.newInstance(A_TREE_SET_TYPE);
      ctor.dup();
      ctor.invokeConstructor(A_TREE_SET_TYPE, CTOR_DEFAULT);
      ctor.putField(self, fieldName, A_SET_TYPE);
      return index + 1;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // LexicalConcat with default are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter method) {
      // LexicalConcat with are not included in hash code.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.of(A_STRING_TYPE);
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // LexicalConcat with default is always ok
    }

    @Override
    public void loadConstructorArgument() {
      delimiter.accept(newRenderer);
    }
  }

  private class Matches extends Element {
    private final Consumer<Renderer> condition;
    private final String fieldName;
    private final Match matchType;

    public Matches(String fieldName, Match matchType, Consumer<Renderer> condition) {
      this.fieldName = fieldName;
      this.matchType = matchType;
      this.condition = condition;
      buildGetter(BOOLEAN_TYPE, fieldName);
      classVisitor
          .visitField(
              Opcodes.ACC_PUBLIC, fieldName + "$stop", BOOLEAN_TYPE.getDescriptor(), null, null)
          .visitEnd();
    }

    @Override
    public void buildCollect() {
      final Label skip = collectRenderer.methodGen().newLabel();
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName + "$stop", BOOLEAN_TYPE);
      collectRenderer.methodGen().ifZCmp(GeneratorAdapter.NE, skip);

      condition.accept(collectRenderer);
      collectRenderer.methodGen().push(matchType.stopOnPredicateMatches());
      collectRenderer.methodGen().ifICmp(GeneratorAdapter.NE, skip);

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().push(true);
      collectRenderer.methodGen().putField(self, fieldName + "$stop", BOOLEAN_TYPE);

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().push(matchType.shortCircuitResult());
      collectRenderer.methodGen().putField(self, fieldName, BOOLEAN_TYPE);

      collectRenderer.methodGen().mark(skip);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      ctor.push(!matchType.shortCircuitResult());
      ctor.putField(self, fieldName, BOOLEAN_TYPE);
      return index;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Partition counters are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter hashMethod) {
      // Partition counters are not included in the hash.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // Do nothing
    }

    @Override
    public void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class NamedTuple extends BaseComposite {

    public NamedTuple(String prefix, Stream<Pair<String, Imyhat>> fields) {
      super(prefix + " ");
      final List<Pair<String, Imyhat>> fieldInfo =
          fields.sorted(Comparator.comparing(Pair::first)).collect(Collectors.toList());
      final GeneratorAdapter getMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              new Method(prefix, A_TUPLE_TYPE, new Type[] {}),
              null,
              null,
              classVisitor);
      getMethod.visitCode();
      getMethod.loadThis();
      getMethod.newInstance(A_TUPLE_TYPE);
      getMethod.dup();
      getMethod.push(fieldInfo.size());
      getMethod.newArray(A_OBJECT_TYPE);
      for (int i = 0; i < fieldInfo.size(); i++) {
        getMethod.dup();
        getMethod.push(i);
        getMethod.loadThis();
        final Type type = fieldInfo.get(i).second().apply(TO_ASM);
        getMethod.invokeVirtual(
            self, new Method(prefix + " " + fieldInfo.get(i).first(), type, new Type[] {}));
        getMethod.valueOf(type);
        getMethod.arrayStore(A_OBJECT_TYPE);
      }
      getMethod.invokeConstructor(A_TUPLE_TYPE, CTOR_TUPLE);
      getMethod.returnValue();
      getMethod.visitMaxs(0, 0);
      getMethod.visitEnd();
    }

    @Override
    public void buildCollect() {
      buildInnerCollect();
    }
  }

  private class OnlyIf extends Element {
    private final String fieldName;
    private final Consumer<Renderer> loader;
    private final Imyhat valueType;

    private OnlyIf(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      super();
      this.valueType = valueType;
      this.fieldName = fieldName;
      this.loader = loader;
      classVisitor
          .visitField(Opcodes.ACC_PUBLIC, fieldName, A_SET_TYPE.getDescriptor(), null, null)
          .visitEnd();
      final GeneratorAdapter getMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              new Method(fieldName, valueType.apply(TO_ASM), new Type[] {}),
              null,
              null,
              classVisitor);
      getMethod.visitCode();
      getMethod.loadThis();
      getMethod.getField(self, fieldName, A_SET_TYPE);
      getMethod.invokeInterface(A_SET_TYPE, SET__ITERATOR);
      getMethod.invokeInterface(A_ITERATOR_TYPE, ITERATOR__NEXT);
      getMethod.unbox(valueType.apply(TO_ASM));
      getMethod.returnValue();
      getMethod.visitMaxs(0, 0);
      getMethod.visitEnd();
    }

    @Override
    public void buildCollect() {
      loader.accept(collectRenderer);
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName, A_SET_TYPE);
      LambdaBuilder.pushInterface(
          collectRenderer,
          "add",
          LambdaBuilder.consumerErasingReturn(Type.BOOLEAN_TYPE, A_OBJECT_TYPE),
          A_SET_TYPE);
      collectRenderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__IF_PRESENT);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      Renderer.loadImyhatInMethod(ctor, valueType.descriptor());
      ctor.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_SET);
      ctor.putField(self, fieldName, A_SET_TYPE);
      return index;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // OnlyIf are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter hashMethod) {
      // OnlyIf are not included in the hash.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      okMethod.loadThis();
      okMethod.getField(self, fieldName, A_SET_TYPE);
      okMethod.invokeInterface(A_SET_TYPE, SET__SIZE);
      okMethod.push(1);
      final Label next = okMethod.newLabel();
      okMethod.ifICmp(GeneratorAdapter.EQ, next);
      okMethod.push(0);
      okMethod.returnValue();
      okMethod.mark(next);
    }

    @Override
    public void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class Optima extends Element {

    private final Comparison comparison;
    private final String fieldName;
    private final Type fieldType;
    private final Consumer<Renderer> loader;

    public Optima(Type fieldType, String fieldName, boolean max, Consumer<Renderer> loader) {
      this.fieldType = fieldType;
      this.fieldName = fieldName;
      comparison = max ? Comparison.GT : Comparison.LT;
      this.loader = loader;
      buildGetter(fieldType, fieldName);
      classVisitor
          .visitField(
              Opcodes.ACC_PUBLIC, fieldName + "$ok", BOOLEAN_TYPE.getDescriptor(), null, null)
          .visitEnd();
    }

    @Override
    public void buildCollect() {
      final int local = collectRenderer.methodGen().newLocal(fieldType);
      loader.accept(collectRenderer);
      collectRenderer.methodGen().storeLocal(local);

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName + "$ok", BOOLEAN_TYPE);
      final Label store = collectRenderer.methodGen().newLabel();
      final Label end = collectRenderer.methodGen().newLabel();
      collectRenderer.methodGen().ifZCmp(GeneratorAdapter.EQ, store);

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().push(true);
      collectRenderer.methodGen().putField(self, fieldName + "$ok", BOOLEAN_TYPE);

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName, fieldType);
      collectRenderer.methodGen().loadLocal(local);

      if (fieldType.equals(Type.LONG_TYPE)) {
        comparison.branchInt(end, collectRenderer.methodGen());
      } else if (fieldType.equals(Type.DOUBLE_TYPE)) {
        comparison.branchFloat(end, collectRenderer.methodGen());
      } else {
        comparison.branchDate(end, collectRenderer.methodGen());
      }

      collectRenderer.methodGen().mark(store);
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().loadLocal(local);
      collectRenderer.methodGen().putField(self, fieldName, fieldType);
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().push(true);
      collectRenderer.methodGen().putField(self, fieldName + "$ok", BOOLEAN_TYPE);
      collectRenderer.methodGen().mark(end);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      return index;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Optima are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter method) {
      // Optima are not included in hash code.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      okMethod.loadThis();
      okMethod.getField(self, fieldName + "$ok", BOOLEAN_TYPE);
      final Label next = okMethod.newLabel();
      okMethod.ifZCmp(GeneratorAdapter.NE, next);
      okMethod.push(false);
      okMethod.returnValue();
      okMethod.mark(next);
    }

    @Override
    public void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class OptimaWithDefault extends Element {

    private final Comparison comparison;
    private final String fieldName;
    private final Type fieldType;
    private final Consumer<Renderer> initial;
    private final Consumer<Renderer> loader;

    public OptimaWithDefault(
        Type fieldType,
        String fieldName,
        boolean max,
        Consumer<Renderer> loader,
        Consumer<Renderer> intial) {
      this.fieldType = fieldType;
      this.fieldName = fieldName;
      initial = intial;
      comparison = max ? Comparison.GT : Comparison.LT;
      this.loader = loader;
      buildGetter(fieldType, fieldName);
    }

    @Override
    public void buildCollect() {
      final int local = collectRenderer.methodGen().newLocal(fieldType);
      loader.accept(collectRenderer);
      collectRenderer.methodGen().storeLocal(local);

      final Label end = collectRenderer.methodGen().newLabel();

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName, fieldType);
      collectRenderer.methodGen().loadLocal(local);

      if (fieldType.equals(Type.LONG_TYPE)) {
        comparison.branchInt(end, collectRenderer.methodGen());
      } else {
        comparison.branchDate(end, collectRenderer.methodGen());
      }

      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().loadLocal(local);
      collectRenderer.methodGen().putField(self, fieldName, fieldType);
      collectRenderer.methodGen().mark(end);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      ctor.loadArg(index);
      ctor.putField(self, fieldName, fieldType);
      return index + 1;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Optima with default are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter method) {
      // Optima with default are not included in hash code.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.of(fieldType);
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // Optima with deafults are always ok.
    }

    @Override
    public void loadConstructorArgument() {
      initial.accept(newRenderer);
    }
  }

  private class PartitionCounter extends Element {
    private final Consumer<Renderer> condition;
    private final String fieldName;

    public PartitionCounter(String fieldName, Consumer<Renderer> condition) {
      this.fieldName = fieldName;
      this.condition = condition;
      classVisitor
          .visitField(
              Opcodes.ACC_PUBLIC, fieldName, A_PARTITION_COUNT_TYPE.getDescriptor(), null, null)
          .visitEnd();
      final GeneratorAdapter getMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              new Method(fieldName, A_TUPLE_TYPE, new Type[] {}),
              null,
              null,
              classVisitor);
      getMethod.visitCode();
      getMethod.loadThis();
      getMethod.getField(self, fieldName, A_PARTITION_COUNT_TYPE);
      getMethod.invokeVirtual(A_PARTITION_COUNT_TYPE, METHOD_PARTITION_COUNT__TO_TUPLE);
      getMethod.returnValue();
      getMethod.visitMaxs(0, 0);
      getMethod.visitEnd();
    }

    @Override
    public void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName, A_PARTITION_COUNT_TYPE);
      condition.accept(collectRenderer);
      collectRenderer
          .methodGen()
          .invokeVirtual(A_PARTITION_COUNT_TYPE, METHOD_PARTITION_COUNT__ACCUMULATE);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      ctor.newInstance(A_PARTITION_COUNT_TYPE);
      ctor.dup();
      ctor.invokeConstructor(A_PARTITION_COUNT_TYPE, CTOR_DEFAULT);
      ctor.putField(self, fieldName, A_PARTITION_COUNT_TYPE);
      return index;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Partition counters are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter hashMethod) {
      // Partition counters are not included in the hash.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // Do nothing
    }

    @Override
    public void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class Univalued extends Element {
    private final String fieldName;
    private final Consumer<Renderer> loader;
    private final Imyhat valueType;

    private Univalued(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
      super();
      this.valueType = valueType;
      this.fieldName = fieldName;
      this.loader = loader;
      classVisitor
          .visitField(Opcodes.ACC_PUBLIC, fieldName, A_SET_TYPE.getDescriptor(), null, null)
          .visitEnd();
      final GeneratorAdapter getMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              new Method(fieldName, valueType.apply(TO_ASM), new Type[] {}),
              null,
              null,
              classVisitor);
      getMethod.visitCode();
      getMethod.loadThis();
      getMethod.getField(self, fieldName, A_SET_TYPE);
      getMethod.invokeInterface(A_SET_TYPE, SET__ITERATOR);
      getMethod.invokeInterface(A_ITERATOR_TYPE, ITERATOR__NEXT);
      getMethod.unbox(valueType.apply(TO_ASM));
      getMethod.returnValue();
      getMethod.visitMaxs(0, 0);
      getMethod.visitEnd();
    }

    @Override
    public void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName, A_SET_TYPE);
      loader.accept(collectRenderer);
      collectRenderer.methodGen().valueOf(valueType.apply(TO_ASM));
      collectRenderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__ADD);
      collectRenderer.methodGen().pop();
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      Renderer.loadImyhatInMethod(ctor, valueType.descriptor());
      ctor.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_SET);
      ctor.putField(self, fieldName, A_SET_TYPE);
      return index;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Univalued are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter hashMethod) {
      // Univalued are not included in the hash.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.empty();
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      okMethod.loadThis();
      okMethod.getField(self, fieldName, A_SET_TYPE);
      okMethod.invokeInterface(A_SET_TYPE, SET__SIZE);
      okMethod.push(1);
      final Label next = okMethod.newLabel();
      okMethod.ifICmp(GeneratorAdapter.EQ, next);
      okMethod.push(0);
      okMethod.returnValue();
      okMethod.mark(next);
    }

    @Override
    public void loadConstructorArgument() {
      // No argument to constructor.
    }
  }

  private class UnivaluedWithDefault extends Element {
    private final Consumer<Renderer> defaultValue;
    private final String fieldName;
    private final Consumer<Renderer> loader;
    private final Imyhat valueType;

    private UnivaluedWithDefault(
        Imyhat valueType,
        String fieldName,
        Consumer<Renderer> loader,
        Consumer<Renderer> defaultValue) {
      super();
      this.valueType = valueType;
      this.fieldName = fieldName;
      this.loader = loader;
      this.defaultValue = defaultValue;
      classVisitor
          .visitField(
              Opcodes.ACC_PUBLIC,
              fieldName,
              A_UNIVALUED_GROUP_ACCUMULATOR_TYPE.getDescriptor(),
              null,
              null)
          .visitEnd();
      final GeneratorAdapter getMethod =
          new GeneratorAdapter(
              Opcodes.ACC_PUBLIC,
              new Method(fieldName, valueType.apply(TO_ASM), new Type[] {}),
              null,
              null,
              classVisitor);
      getMethod.visitCode();
      getMethod.loadThis();
      getMethod.getField(self, fieldName, A_UNIVALUED_GROUP_ACCUMULATOR_TYPE);
      getMethod.invokeVirtual(
          A_UNIVALUED_GROUP_ACCUMULATOR_TYPE, METHOD_UNIVALUED_GROUP_ACCUMULATOR__GET);
      getMethod.unbox(valueType.apply(TO_ASM));
      getMethod.returnValue();
      getMethod.visitMaxs(0, 0);
      getMethod.visitEnd();
    }

    @Override
    public void buildCollect() {
      collectRenderer.methodGen().loadArg(collectedSelfArgument);
      collectRenderer.methodGen().getField(self, fieldName, A_UNIVALUED_GROUP_ACCUMULATOR_TYPE);
      loader.accept(collectRenderer);
      collectRenderer.methodGen().valueOf(valueType.apply(TO_ASM));
      collectRenderer
          .methodGen()
          .invokeVirtual(
              A_UNIVALUED_GROUP_ACCUMULATOR_TYPE, METHOD_UNIVALUED_GROUP_ACCUMULATOR__ADD);
    }

    @Override
    public int buildConstructor(GeneratorAdapter ctor, int index) {
      ctor.loadThis();
      ctor.newInstance(A_UNIVALUED_GROUP_ACCUMULATOR_TYPE);
      ctor.dup();
      ctor.loadArg(index);
      ctor.valueOf(valueType.apply(TO_ASM));
      ctor.invokeConstructor(
          A_UNIVALUED_GROUP_ACCUMULATOR_TYPE, METHOD_UNIVALUED_GROUP_ACCUMULATOR__CTOR);
      ctor.putField(self, fieldName, A_UNIVALUED_GROUP_ACCUMULATOR_TYPE);
      return index + 1;
    }

    @Override
    public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
      // Univalued are not included in equality.
    }

    @Override
    public void buildHashCode(GeneratorAdapter hashMethod) {
      // Univalued are not included in the hash.
    }

    @Override
    public Stream<Type> constructorType() {
      return Stream.of(this.valueType.apply(TO_ASM));
    }

    @Override
    public void failIfBad(GeneratorAdapter okMethod) {
      // Univalued with default is always ok
    }

    @Override
    public void loadConstructorArgument() {
      defaultValue.accept(newRenderer);
    }
  }

  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_ITERATOR_TYPE = Type.getType(Iterator.class);
  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_PARTITION_COUNT_TYPE = Type.getType(PartitionCount.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Type A_TREE_SET_TYPE = Type.getType(TreeSet.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Type A_UNIVALUED_GROUP_ACCUMULATOR_TYPE =
      Type.getType(UnivaluedGroupAccumulator.class);
  private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});
  private static final Method CTOR_TUPLE =
      new Method("<init>", VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private static final Method ITERATOR__NEXT = new Method("next", A_OBJECT_TYPE, new Type[] {});
  private static final Method METHOD_EQUALS =
      new Method("equals", BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_HASH_CODE = new Method("hashCode", INT_TYPE, new Type[] {});
  private static final Method METHOD_IMYHAT__NEW_MAP =
      new Method("newMap", A_MAP_TYPE, new Type[] {});
  private static final Method METHOD_IMYHAT__NEW_SET =
      new Method("newSet", A_SET_TYPE, new Type[] {});
  static final Method METHOD_IS_OK = new Method("is ok?", BOOLEAN_TYPE, new Type[] {});
  private static final Method METHOD_MAP__PUT =
      new Method("put", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE, A_OBJECT_TYPE});
  private static final Method METHOD_OPTIONAL__IF_PRESENT =
      new Method("ifPresent", VOID_TYPE, new Type[] {Type.getType(Consumer.class)});
  private static final Method METHOD_PARTITION_COUNT__ACCUMULATE =
      new Method("accumulate", VOID_TYPE, new Type[] {BOOLEAN_TYPE});
  private static final Method METHOD_PARTITION_COUNT__TO_TUPLE =
      new Method("toTuple", A_TUPLE_TYPE, new Type[] {});
  private static final Method METHOD_SET__ADD =
      new Method("add", BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_SET__ADD_ALL =
      new Method("addAll", BOOLEAN_TYPE, new Type[] {Type.getType(Collection.class)});
  private static final Method METHOD_STRING__JOIN =
      new Method(
          "join",
          A_STRING_TYPE,
          new Type[] {Type.getType(CharSequence.class), Type.getType(Iterable.class)});
  private static final Method METHOD_UNIVALUED_GROUP_ACCUMULATOR__ADD =
      new Method("add", VOID_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_UNIVALUED_GROUP_ACCUMULATOR__CTOR =
      new Method("<init>", VOID_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_UNIVALUED_GROUP_ACCUMULATOR__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {});
  private static final Method SET__ITERATOR =
      new Method("iterator", A_ITERATOR_TYPE, new Type[] {});
  private static final Method SET__SIZE = new Method("size", INT_TYPE, new Type[] {});
  private final ClassVisitor classVisitor;
  private final Renderer collectRenderer;
  public final int collectedSelfArgument;
  private final List<Element> elements = new ArrayList<>();

  private final Renderer newRenderer;

  private final Type self;

  public RegroupVariablesBuilder(
      RootBuilder builder,
      String name,
      Renderer newMethodGen,
      Renderer collectedMethodGen,
      int collectedSelfArgument) {
    newRenderer = newMethodGen;
    collectRenderer = collectedMethodGen;
    this.collectedSelfArgument = collectedSelfArgument;
    self = Type.getObjectType(name);
    classVisitor = builder.createClassVisitor();
    classVisitor.visit(
        Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, A_OBJECT_TYPE.getInternalName(), null);
    classVisitor.visitSource(builder.sourcePath(), null);
  }

  @Override
  public void addCollected(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
    elements.add(new Collected(valueType, fieldName, loader));
  }

  @Override
  public void addCollected(
      Imyhat keyType,
      Imyhat valueType,
      String fieldName,
      Consumer<Renderer> keyLoader,
      Consumer<Renderer> valueLoader) {
    elements.add(new Dictionary(keyType, valueType, fieldName, keyLoader, valueLoader));
  }

  @Override
  public void addCount(String fieldName) {
    elements.add(new Count(fieldName));
  }

  @Override
  public void addFirst(Type fieldType, String fieldName, Consumer<Renderer> loader) {
    elements.add(new First(fieldType, fieldName, loader));
  }

  @Override
  public void addFirst(
      Type fieldType, String fieldName, Consumer<Renderer> loader, Consumer<Renderer> initial) {
    elements.add(new FirstWithDefault(fieldType, fieldName, loader, initial));
  }

  @Override
  public void addFlatten(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
    elements.add(new Flattened(valueType, fieldName, loader));
  }

  public void addKey(Type fieldType, String fieldName, Consumer<Renderer> loader) {
    elements.add(new Discriminator(fieldType, fieldName, loader));
  }

  @Override
  public void addLexicalConcat(
      String fieldName, Consumer<Renderer> loader, Consumer<Renderer> delimiterLoader) {
    elements.add(new LexicalConcat(fieldName, loader, delimiterLoader));
  }

  @Override
  public void addMatches(String name, Match matchType, Consumer<Renderer> condition) {
    elements.add(new Matches(name, matchType, condition));
  }

  @Override
  public Regrouper addObject(String fieldName, Stream<Pair<String, Imyhat>> fields) {
    final NamedTuple namedTuple = new NamedTuple(fieldName, fields);
    elements.add(namedTuple);
    return namedTuple;
  }

  @Override
  public void addOnlyIf(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
    elements.add(new OnlyIf(valueType, fieldName, loader));
  }

  @Override
  public void addOptima(Type fieldType, String fieldName, boolean max, Consumer<Renderer> loader) {
    elements.add(new Optima(fieldType, fieldName, max, loader));
  }

  @Override
  public void addOptima(
      Type fieldType,
      String fieldName,
      boolean max,
      Consumer<Renderer> loader,
      Consumer<Renderer> initial) {
    elements.add(new OptimaWithDefault(fieldType, fieldName, max, loader, initial));
  }

  @Override
  public void addPartitionCount(String fieldName, Consumer<Renderer> condition) {
    elements.add(new PartitionCounter(fieldName, condition));
  }

  @Override
  public void addUnivalued(Imyhat valueType, String fieldName, Consumer<Renderer> loader) {
    elements.add(new Univalued(valueType, fieldName, loader));
  }

  @Override
  public void addUnivalued(
      Imyhat valueType,
      String fieldName,
      Consumer<Renderer> loader,
      Consumer<Renderer> defaultValue) {
    elements.add(new UnivaluedWithDefault(valueType, fieldName, loader, defaultValue));
  }

  @Override
  public Regrouper addWhere(Consumer<Renderer> condition) {
    final Conditional c = new Conditional(condition, "");
    elements.add(c);
    return c;
  }

  private void buildGetter(Type fieldType, String fieldName) {
    classVisitor
        .visitField(Opcodes.ACC_PUBLIC, fieldName, fieldType.getDescriptor(), null, null)
        .visitEnd();
    final GeneratorAdapter getMethod =
        new GeneratorAdapter(
            Opcodes.ACC_PUBLIC,
            new Method(fieldName, fieldType, new Type[] {}),
            null,
            null,
            classVisitor);
    getMethod.visitCode();
    getMethod.loadThis();
    getMethod.getField(self, fieldName, fieldType);
    getMethod.returnValue();
    getMethod.visitMaxs(0, 0);
    getMethod.visitEnd();
  }

  /** Generate the class completely */
  public void finish() {
    final Method ctorType =
        new Method(
            "<init>",
            VOID_TYPE,
            elements.stream().flatMap(Element::constructorType).toArray(Type[]::new));
    final GeneratorAdapter ctor =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, ctorType, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_OBJECT_TYPE, CTOR_DEFAULT);
    int index = 0;
    for (final Element element : elements) {
      index = element.buildConstructor(ctor, index);
    }
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    final GeneratorAdapter hashMethod =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_HASH_CODE, null, null, classVisitor);
    hashMethod.visitCode();
    hashMethod.push(0);
    elements.forEach(element -> element.buildHashCode(hashMethod));

    hashMethod.returnValue();
    hashMethod.visitMaxs(0, 0);
    hashMethod.visitEnd();

    final GeneratorAdapter equalsMethod =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_EQUALS, null, null, classVisitor);
    equalsMethod.visitCode();
    equalsMethod.loadArg(0);
    equalsMethod.instanceOf(self);
    final Label equalsFalse = equalsMethod.newLabel();
    equalsMethod.ifZCmp(GeneratorAdapter.EQ, equalsFalse);
    final int equalsOtherLocal = equalsMethod.newLocal(self);
    equalsMethod.loadArg(0);
    equalsMethod.checkCast(self);
    equalsMethod.storeLocal(equalsOtherLocal);
    elements.forEach(element -> element.buildEquals(equalsMethod, equalsOtherLocal, equalsFalse));
    equalsMethod.push(true);
    equalsMethod.returnValue();
    equalsMethod.mark(equalsFalse);
    equalsMethod.push(false);
    equalsMethod.returnValue();
    equalsMethod.visitMaxs(0, 0);
    equalsMethod.visitEnd();

    newRenderer.methodGen().visitCode();
    newRenderer.methodGen().newInstance(self);
    newRenderer.methodGen().dup();
    elements.forEach(Element::loadConstructorArgument);
    newRenderer.methodGen().invokeConstructor(self, ctorType);
    newRenderer.methodGen().returnValue();
    newRenderer.methodGen().visitMaxs(0, 0);
    newRenderer.methodGen().visitEnd();

    collectRenderer.methodGen().visitCode();
    elements.forEach(Element::buildCollect);
    collectRenderer.methodGen().visitInsn(Opcodes.RETURN);
    collectRenderer.methodGen().visitMaxs(0, 0);
    collectRenderer.methodGen().visitEnd();

    final GeneratorAdapter okMethod =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_IS_OK, null, null, classVisitor);
    okMethod.visitCode();
    elements.forEach(element -> element.failIfBad(okMethod));
    okMethod.push(true);
    okMethod.returnValue();
    okMethod.visitMaxs(0, 0);
    okMethod.visitEnd();

    classVisitor.visitEnd();
  }
}
