package ca.on.oicr.gsi.shesmu.github;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.util.input.BaseJsonInputRepository;

@MetaInfServices(GithubBranchesRepository.class)
public class GithubBranchJsonRepository extends BaseJsonInputRepository<GithubBranchValue>
		implements GithubBranchesRepository {

	public GithubBranchJsonRepository() {
		super("github_branches");
	}

	@Override
	protected GithubBranchValue convert(ObjectNode node) {
		return new GithubBranchValue() {

			@Override
			public String branch() {
				return node.get("branch").asText();
			}

			@Override
			public String commit() {
				return node.get("commit").asText();
			}

			@Override
			public String owner() {
				return node.get("owner").asText();
			}

			@Override
			public String repository() {
				return node.get("repository").asText();
			}
		};
	}

}
