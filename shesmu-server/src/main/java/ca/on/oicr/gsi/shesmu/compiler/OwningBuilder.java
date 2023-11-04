package ca.on.oicr.gsi.shesmu.compiler;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

public interface OwningBuilder {

  ClassVisitor classVisitor();

  Type selfType();

  String sourceLocation(int line, int column);
}
