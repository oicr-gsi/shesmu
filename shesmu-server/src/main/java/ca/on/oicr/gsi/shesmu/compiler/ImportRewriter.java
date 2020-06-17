package ca.on.oicr.gsi.shesmu.compiler;

public interface ImportRewriter {

  ImportRewriter NULL = name -> name;

  String rewrite(String name);
}
