#!/bin/bash

set -eux

MAIN_BRANCH="master"

# Fail if git working directory is dirty
if [[ ! $(git branch | grep \* | cut -d ' ' -f2) = "${MAIN_BRANCH}" ]]; then
  echo "Error: Not on ${MAIN_BRANCH} branch" >&2
  exit 3
fi
git fetch
if (( $(git log HEAD..origin/${MAIN_BRANCH} --oneline | wc -l) > 0 )); then
  echo "Error: Branch is not up-to-date with remote origin" >&2
  exit 4
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "git working directory is dirty. Fix this, then re-attempt."
  exit 2
fi

OLD_VERSION="$(xmlstarlet sel -t -v /_:project/_:version pom.xml | sed -e s/-SNAPSHOT//g)"

# Get new version from user
echo "${OLD_VERSION}" >version.temp
${EDITOR:-editor} version.temp
NEW_VERSION="$(cat version.temp)"
rm version.temp

if git tag --list | grep -c -E "^v${NEW_VERSION}$" >/dev/null; then
	echo "Version $NEW_VERSION already exists. Please restart and select a different version number."
	exit 3
fi

# Update the release notes
if ! grep -q "# Unreleased" RELEASE_NOTES.md; then
	echo "Error: changelog does not contain an Unreleased section"
	exit 4
fi
mv RELEASE_NOTES.md RELEASE_NOTES.md.old
sed "s/# Unreleased/# Unreleased\n\n# \[${NEW_VERSION}\] - $(date -u +%Y-%m-%dT%H:%M+00:00)/g" RELEASE_NOTES.md.old > RELEASE_NOTES.md
rm RELEASE_NOTES.md.old
${EDITOR:-editor} RELEASE_NOTES.md
git commit -a -m "Update release notes for release"

# Do the Maven release step
mvn release:prepare -DreleaseVersion=${NEW_VERSION} -DtagNameFormat=v@{version} --batch-mode release:clean
git checkout v${NEW_VERSION}
mvn deploy
git checkout master
# Trigger GitHub Actions to build the Maven project and build the Docker image
github-release -v release -s ${GITHUB_TOKEN} -u oicr-gsi -r shesmu -t v${NEW_VERSION} -d "[Release Notes](https://github.com/oicr-gsi/shesmu/blob/master/RELEASE_NOTES.md#$(echo "${NEW_VERSION}" | sed s/\.//g))" | grep BODY: | cut -f 2- -d: | jq -r .html_url

set +x

echo "export SHESMU_VERSION=${NEW_VERSION}"
