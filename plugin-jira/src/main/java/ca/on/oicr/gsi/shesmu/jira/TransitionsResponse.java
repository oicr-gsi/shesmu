package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransitionsResponse(List<Transition> transitions) {}
