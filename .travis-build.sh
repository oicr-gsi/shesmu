#!/bin/sh

set -eu

case "${JAVA_HOME}" in
	*8*)
		PINERY=
		SONAR=true
		;;
	*)
		PINERY="-pl !plugin-niassa+pinery"
		SONAR=false
		;;
esac

mvn ${PINERY} clean install
echo ${SONAR} ${TRAVIS_PULL_REQUEST}  ${TRAVIS_PULL_REQUEST_SLUG} ${TRAVIS_REPO_SLUG}
if ${SONAR}
then
	if [ "${TRAVIS_PULL_REQUEST}" = "false" ] || [ "${TRAVIS_PULL_REQUEST_SLUG}" = "${TRAVIS_REPO_SLUG}" ]
	then
		mvn org.jacoco:jacoco-maven-plugin:prepare-agent sonar:sonar
	else
		echo "[WARN] SonarCloud cannot run on pull requests from forks."
	fi
fi
