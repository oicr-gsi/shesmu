package ca.on.oicr.gsi.shesmu.configmaster;

import ca.on.oicr.gsi.shesmu.configmaster.Files.File;
import ca.on.oicr.gsi.shesmu.configmaster.Workflows.Workflow;
import java.util.HashSet;
import java.util.Set;

public class Case {
  private Set<Workflow> requiredWorkflows = new HashSet<>();
  private Set<File> djerbaRequirements = new HashSet<>(),
      pipelineRequirements = new HashSet<>(),
      extraRequirements = new HashSet<>(); // Questionable; not implemented in demo

  public Case(String assayTest, Set<String> assayDeliverables) {
    if (assayDeliverables.contains("Clinical Report")) {
      djerbaRequirements.addAll(Djerba.getRequirements(assayTest));
    }
    if (assayDeliverables.contains("QC")) {
      pipelineRequirements.addAll(QcMapping.getRequirements(assayDeliverables));
    }

    // This would probably come from another config in actual implementation
    if (assayDeliverables.contains("Fastq")) {
      pipelineRequirements.add(Files.getByName("fastq"));
    }
    recalculateRequiredWorkflows();
  }

  private void recalculateRequiredWorkflows() {
    Set<File> allRequirements = new HashSet<>(djerbaRequirements);
    allRequirements.addAll(pipelineRequirements);
    allRequirements.addAll(extraRequirements);

    Set<Workflow> temp = new HashSet<>();
    for (File requiredFile : allRequirements) {
      for (Workflow upstream : Workflows.whatOutputs(requiredFile)) {
        temp.addAll(Workflows.getAllUpstreamFrom(upstream));
      }
    }
    this.requiredWorkflows = temp;
  }

  /**
   * We could use this in a shesmu function for checking when a case is ready for Djerba, to then
   * alert or launch djerba.
   *
   * @return Files required by Djerba for this case's config
   */
  public Set<File> getDjerbaRequirements() {
    return djerbaRequirements;
  }

  /**
   * We could use this in a shesmu function for checking whether an olive ought to run for a
   * particular case, resulting in a one-line filter
   *
   * @param potentialWorkflow workflow we'd like to know if this case requires
   * @return boolean
   */
  public boolean isWorkflowRequired(Workflow potentialWorkflow) {
    return requiredWorkflows.contains(potentialWorkflow);
  }
}
