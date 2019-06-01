package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.sourceforge.seqware.common.model.Annotatable;
import net.sourceforge.seqware.common.model.Attribute;

/** Action to annotate a SeqWare/Niassa object */
public final class AnnotationAction<A extends Attribute<?, A>> extends Action {

  @ActionParameter public String accession;
  @ActionParameter public String key;
  private final Supplier<NiassaServer> server;
  private final AnnotationType<A> type;
  @ActionParameter public String value;

  AnnotationAction(Supplier<NiassaServer> server, AnnotationType<A> type) {
    super("niassa-annotation");
    this.server = server;
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AnnotationAction that = (AnnotationAction) o;
    return Objects.equals(type, that.type)
        && Objects.equals(accession, that.accession)
        && Objects.equals(key, that.key)
        && Objects.equals(value, that.value);
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return Optional.empty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, accession, key, value);
  }

  @Override
  public final ActionState perform(ActionServices actionServices) {
    try {
      final int accession = Integer.parseInt(this.accession);
      final Annotatable<A> item = type.fetch(server.get().metadata(), accession);
      if (item.getAnnotations()
          .stream()
          .anyMatch(a -> a.getTag().equals(key) && a.getValue().equals(value))) {
        return ActionState.SUCCEEDED;
      }

      final A attribute = type.create();
      attribute.setTag(key);
      attribute.setValue(value);
      type.save(server.get().metadata(), accession, attribute);
      return ActionState.SUCCEEDED;
    } catch (final Exception e) {
      e.printStackTrace();
      return ActionState.FAILED;
    }
  }

  @Override
  public final int priority() {
    return 0;
  }

  @Override
  public final long retryMinutes() {
    return 10;
  }

  @Override
  public boolean search(Pattern query) {
    return query.matcher(accession).matches()
        || query.matcher(key).matches()
        || query.matcher(value).matches();
  }

  @Override
  public final ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("name", type.name());
    node.put("accession", accession);
    node.put("key", key);
    node.put("value", value);
    return node;
  }
}
