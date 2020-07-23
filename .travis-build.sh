#!/bin/sh

set -eu

case "${JAVA_HOME}" in
	*8*)
		# Disable Sonar entirely because it is broken
		SONAR=false
		;;
	*)
		SONAR=false
		;;
esac

mvn -DskipIT=false clean install
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

cd json-dir-list
autoreconf -i
./configure
make
