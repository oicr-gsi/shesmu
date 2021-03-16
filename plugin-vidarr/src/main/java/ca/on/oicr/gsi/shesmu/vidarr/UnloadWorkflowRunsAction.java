package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.vidarr.UnloadFilter;
import ca.on.oicr.gsi.vidarr.UnloadTextSelector;
import ca.on.oicr.gsi.vidarr.api.UnloadFilterWorkflowRunId;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class UnloadWorkflowRunsAction extends BaseUnloadAction {
  @ActionParameter(name = "workflow_runs")
  public Set<String> workflowRuns = Set.of();

  public UnloadWorkflowRunsAction(Supplier<VidarrPlugin> owner) {
    super("runs", owner);
  }

  @Override
  protected void addFilterForJson(ObjectNode node) {
    workflowRuns.forEach(node.putArray("workflowRuns")::add);
  }

  @Override
  protected UnloadFilter createFilter() {
    if (workflowRuns.isEmpty()) {
      return null;
    }
    final var filter = new UnloadFilterWorkflowRunId();
    filter.setId(UnloadTextSelector.of(workflowRuns));
    return filter;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UnloadWorkflowRunsAction that = (UnloadWorkflowRunsAction) o;
    return workflowRuns.equals(that.workflowRuns);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    for (final var run : workflowRuns) {
      digest.accept(run.getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(workflowRuns);
  }

  @Override
  public boolean search(Pattern query) {
    return workflowRuns.stream().anyMatch(query.asPredicate());
  }
}
