#!/bin/sh

set -eu

case "${JAVA_HOME}" in
	*8*)
		PINERY=plugin/niassa+pinery
		SONAR=true
		;;
	*)
		PINERY=
		SONAR=false
		;;
esac

ROOT_PATH="$(pwd)"
for dir in shesmu-server \
	plugin/guanyin \
	plugin/jira \
	plugin/nabu \
	plugin/runscanner \
	plugin/sftp \
	$PINERY
do
	cd "${ROOT_PATH}/$dir"
	mvn clean install
	echo ${SONAR}  ${TRAVIS_PULL_REQUEST}  ${TRAVIS_PULL_REQUEST_SLUG} ${TRAVIS_REPO_SLUG}
	if ${SONAR}
	then
		if [ "${TRAVIS_PULL_REQUEST}" = "false" ] || [ "${TRAVIS_PULL_REQUEST_SLUG}" = "${TRAVIS_REPO_SLUG}" ]
		then
			mvn org.jacoco:jacoco-maven-plugin:prepare-agent sonar:sonar
		else
			echo "[WARN] SonarCloud cannot run on pull requests from forks."
		fi
	fi
done
