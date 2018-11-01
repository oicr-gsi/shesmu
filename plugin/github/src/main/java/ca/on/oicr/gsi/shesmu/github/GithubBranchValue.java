package ca.on.oicr.gsi.shesmu.github;

import ca.on.oicr.gsi.shesmu.util.input.ShesmuVariable;

public abstract class GithubBranchValue {

	@ShesmuVariable
	public abstract String branch();

	@ShesmuVariable(signable = true)
	public abstract String commit();

	@ShesmuVariable
	public abstract String owner();

	@ShesmuVariable
	public abstract String repository();
}
