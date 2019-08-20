package ca.on.oicr.gsi.shesmu.compiler.description;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import java.util.Set;
import java.util.function.Predicate;

public interface Renderable {
  void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate);

  void render(Renderer renderer);
}
