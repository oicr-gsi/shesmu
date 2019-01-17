package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;

import java.time.Instant;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Utility code for generating comparison bytecode */
public enum Comparison {
  EQ(GeneratorAdapter.EQ, false, "=="),
  GE(GeneratorAdapter.GE, true, ">="),
  GT(GeneratorAdapter.GT, true, ">"),
  LE(GeneratorAdapter.LE, true, "<="),
  LT(GeneratorAdapter.LT, true, "<"),
  NE(GeneratorAdapter.NE, false, "!=");

  private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Method METHOD_COMPARE_TO =
      new Method("compareTo", INT_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_EQUALS =
      new Method("equals", BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});
  private final int id;
  private final boolean ordered;
  private final String symbol;

  private Comparison(int id, boolean ordered, String symbol) {
    this.id = id;
    this.ordered = ordered;
    this.symbol = symbol;
  }

  public void branchBool(Label target, GeneratorAdapter methodGen) {
    methodGen.ifCmp(Type.BOOLEAN_TYPE, id, target);
  }

  public void branchDate(Label target, GeneratorAdapter methodGen) {
    methodGen.invokeVirtual(A_INSTANT_TYPE, METHOD_COMPARE_TO);
    methodGen.push(0);
    methodGen.ifCmp(Type.INT_TYPE, id, target);
  }

  public void branchInt(Label target, GeneratorAdapter methodGen) {
    methodGen.ifCmp(Type.LONG_TYPE, id, target);
  }

  public void branchObject(Label target, GeneratorAdapter methodGen) {
    if (isOrdered()) {
      throw new IllegalArgumentException(String.format("Cannot compare %s on object type", name()));
    }
    methodGen.invokeVirtual(A_OBJECT_TYPE, METHOD_EQUALS);
    methodGen.not();
    methodGen.ifZCmp(id, target);
  }

  /** Whether this comparison only works for totally ordered types (integers and dates). */
  public boolean isOrdered() {
    return ordered;
  }

  /** The symbol for this type. */
  public String symbol() {
    return symbol;
  }
}
