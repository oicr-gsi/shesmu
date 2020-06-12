# How to contribute

We're glad you're interested in contributing to the development of Shesmu and
are happy to collaborate.  Please review this document to ensure that your work
fits well into the Shesmu code base.

## Tickets

Create a ticket for your issue if one does not exist. As development of Shesmu
is mainly done at OICR currently, the ticket is usually on the internal OICR
JIRA, but a GitHub Issue is also acceptable.  This ensures that we have a place
to discuss the changes before the work is done. A ticket is not necessary if
the change is trivial, such as correcting a typo.

## Branches

Create a feature branch. The branch should be based on the `master` branch
unless you have reason to do otherwise. The branch name should begin with the
issue number, and be followed by a brief hint of what it is about. _e.g._,
`#1234_fix_jira_action`

## Code Formatting

The project includes Google code formatter for Java that should be
automatically run on check-in using Maven. For JavaScript and TypeScript, we
use [prettier](https://prettier.io/) manually. Please ensure that these are
used. This keeps the diff clean, so it is easier to review your changes.

## Testing

We have several types of automated testing:

* Compiler tests -- these tests make sure that olive language compiler produce the expected output (either success or error messages)
  * Run with `mvn clean install`
  * Tests are in `shesmu-server/src/test/resources/compiler` and `shesmu-server/src/test/resources/compiler`
  * All new syntax changes require tests
  * Fixes for bad bytecode generation require tests
* User interface tests
  * Run with `mvn clean install`
  * Tests are in `shesmu-server/src/test/java/ca/on/oicr/gsi/UserInterfaceTest.java`
* Miscellaneous tests
  * Run with `mvn clean install`
  * Tests are in `shesmu-server/src/test/java/ca/on/oicr/gsi/`
* Plugin tests
  * Run with `mvn clean install`

Please make sure to add or update the relevant tests for your changes. Testing
plugins is difficult because they are often mostly integration code that has to
communicate with a external services. We accept any level of testing on plugins
as long as it is fast and delegate any plugin bugs to plugin authors.

## Commits

* Make sure your commit contains a reference to the issue number. _e.g._
  `Closes #1234` in the body Edit the **Unreleased** section in
* `RELEASE_NOTES.md` to detail any user-visible **Changes** or
	**Update Notes** (Additional steps that must be taken when upgrading to the
  Shesmu version containing your change)

## Pull Requests

Changes should never be merged directly into `master`. Pull requests should be
made into the `master` branch for testing and review. All pull requests need
two reviewers. If you have suggestions, please select them when creating your
pull request, but the Shesmu developers may add additional developers or assign
someone else.

## Merging

Once all of the tests are passing, and your pull request has received two
approvals, you are ready to merge. To keep a clean commit history please

1. Merge your changes into one commit unless they are clearly separate changes.
2. Rebase on the `master` branch so that your change appears after the changes
   that were previously merged in the history.

To do this, on your system:

    git fetch && git rebase -i origin/master

Then change any commits that have no semantic meaning to `f` fixup commits to
be absorbed into the previous commit. Finally, push the modified version to
your branch with:

    git push -f

Please delete your feature branch after it is merged.

## Plugins versus Core Infrastructure
Shesmu has two very different parts: the core Shesmu infrastructure (the server,
the compiler, and testing infrastructure) and plugins for integration with
other systems. We are happy to take on plugins for systems we do not use and
provide them with some maintenance and to include them in our release process
to make them as available as possible.

However, the core Shesmu team does not have the testing environment and
therefore cannot support every plugin contributed by the community. If you are
contributing a plugin, there is an expectation that you or your organisation
are committed to ongoing maintenance of that plugin and handling issues
associated with it. Plugins that become a maintenance burden for the core team
will be removed from the main repository.

If you wish to maintain a plugin in an external repository, we will happily add
links to core documentation for it.

Contributions to the core infrastructure will be maintained by the core
developers.

## Changes to the Shesmu Language
Every change to the language itself is effectively permanent. We must be
conservative and sure that we aren't making a change we will regret later or
something which is a large maintenance burden for minimal value. We also need
to make sure that changes do not impact existing users negatively. Expect slow
and cautious review of syntax changes.

### Thank you for your contributions!
