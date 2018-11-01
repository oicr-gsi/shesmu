package ca.on.oicr.gsi.shesmu.github;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.util.input.BaseInputFormatDefinition;

@MetaInfServices(InputFormatDefinition.class)
public class GithubBranchesFormatDefinition
		extends BaseInputFormatDefinition<GithubBranchValue, GithubBranchesRepository> {

	public GithubBranchesFormatDefinition() {
		super("github_branches", GithubBranchValue.class, GithubBranchesRepository.class);
	}

}
