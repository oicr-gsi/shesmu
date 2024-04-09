#!/bin/bash

set -eux

MAIN_BRANCH="master"
CHANGE_DIR="changes"
RELEASE_TYPE=""
NEW_VERSION=""

# validate arguments
usage_error() {
  echo "Error: bad arguments" >&2
  echo "Usage: $0 [major|minor]" >&2
  exit 1
}

if [[ "$#" -eq 1 ]]; then
  if [[ "$1" = "major" ]] || [[ "$1" = "minor" ]]; then
    RELEASE_TYPE="$1"
  else
    usage_error
  fi
elif [ "$#" -gt 1 ]; then
  usage_error
fi


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

# Get new version from changes
MAJOR=$(echo $OLD_VERSION | cut -d . -f 1 -)
MINOR=$(echo $OLD_VERSION | cut -d . -f 2 -)
PATCH=$(echo $OLD_VERSION | cut -d . -f 3 -)

if [[ "${RELEASE_TYPE}" = "major" ]]; then
  MAJOR=$((MAJOR+1))
  MINOR=0
  PATCH=0
elif [[ "${RELEASE_TYPE}" = "minor" ]] || [[ -n $(find "${CHANGE_DIR}" -mindepth 1 -maxdepth 1 \
    -name "add_*" -or -name "change_*" -or -name "remove_*") ]]; then
  RELEASE_TYPE="minor"
  MINOR=$((MINOR+1))
  PATCH=0
elif [[ -n $(find "${CHANGE_DIR}" -mindepth 1 -maxdepth 1 -name "fix_*") ]]; then
  RELEASE_TYPE="patch"
  # use patch version from snapshot version
else
  echo "No changes found in 'changes' directory. Aborting release." >&2
  exit 5
fi

NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"

if git tag --list | grep -c -E "^v${NEW_VERSION}$" >/dev/null; then
	echo "Version $NEW_VERSION already exists. Please restart and select a different version number."
	exit 3
fi

# Update the release notes
echo "Preparing ${RELEASE_TYPE} release ${NEW_VERSION}..."
./compact-changelog.sh ${NEW_VERSION} || exit 2
git commit -a -m "Update release notes for release"

# Release
mvn versions:set -DnewVersion=${NEW_VERSION} -DgenerateBackupPoms=false
sed -i -e 's/^version = .*$/version = "'$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout | cut -f 1 -d -)'"/' */Cargo.toml
git commit -a -m "prepare release v${NEW_VERSION}"
git tag -a v${NEW_VERSION} -m "prepare v${NEW_VERSION} release"
mvn clean install
mvn versions:set -DnextSnapshot=true -DgenerateBackupPoms=false
git commit -a -m "prepared for next development iteration"
git push origin master
git push origin v${NEW_VERSION}

# Deploy
git checkout v${NEW_VERSION}
mvn deploy
git checkout master
# Trigger GitHub Actions to build the Maven project and build the Docker image
github-release -v release -s ${GITHUB_TOKEN} -u oicr-gsi -r shesmu -t v${NEW_VERSION} -d "[Release Notes](https://github.com/oicr-gsi/shesmu/blob/master/RELEASE_NOTES.md#$(echo "${NEW_VERSION}" | sed s/\.//g))" | grep BODY: | cut -f 2- -d: | jq -r .html_url

set +x

echo "export SHESMU_VERSION=${NEW_VERSION}"
