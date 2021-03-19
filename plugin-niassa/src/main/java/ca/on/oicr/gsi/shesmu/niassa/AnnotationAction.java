package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.model.Annotatable;
import net.sourceforge.seqware.common.model.Attribute;

/** Action to annotate a SeqWare/Niassa object */
public final class AnnotationAction<A extends Attribute<?, A>> extends Action {

  @SuppressWarnings("rawtypes")
  private static final ActionCommand<AnnotationAction> HUMAN_APPROVE_COMMAND =
      new ActionCommand<AnnotationAction>(
          AnnotationAction.class,
          "NIASSA-HUMAN-APPROVE",
          FrontEndIcon.HAND_THUMBS_UP,
          "Allow to run",
          Preference.ALLOW_BULK,
          Preference.ANNOY_USER) {
        @Override
        protected Response execute(AnnotationAction action, Optional<String> user) {
          if (!action.automatic) {
            action.automatic = true;
            return Response.ACCEPTED;
          }
          return Response.IGNORED;
        }
      };

  @ActionParameter public String accession;

  @ActionParameter(required = false)
  public boolean automatic = true;

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
  public Stream<ActionCommand<?>> commands() {
    return Stream.of(HUMAN_APPROVE_COMMAND);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AnnotationAction<?> that = (AnnotationAction<?>) o;
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
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(type.name().getBytes(StandardCharsets.UTF_8));
    digest.accept(accession.getBytes(StandardCharsets.UTF_8));
    digest.accept(key.getBytes(StandardCharsets.UTF_8));
    digest.accept(new byte[] {0});
    digest.accept(value.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, accession, key, value);
  }

  @Override
  public final ActionState perform(ActionServices actionServices) {
    try {
      final int accession = Integer.parseInt(this.accession);
      final Metadata metadata = server.get().metadata();
      final Annotatable<A> item = type.fetch(metadata, accession);
      if (item.getAnnotations().stream()
          .anyMatch(a -> a.getTag().equals(key) && a.getValue().equals(value))) {
        metadata.clean_up();
        return ActionState.SUCCEEDED;
      }
      if (!automatic) {
        return ActionState.HALP;
      }

      final A attribute = type.create();
      attribute.setTag(key);
      attribute.setValue(value);
      type.save(metadata, accession, attribute);
      metadata.clean_up();
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
