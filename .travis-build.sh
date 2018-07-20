#!/bin/sh

set -eu

case "${JAVA_HOME}" in
	*jdk8)
		PINERY=plugin/seqware+pinery
		;;
	*)
		PINERY=
		;;
esac

ROOT_PATH="$(pwd)"
for dir in shesmu-server plugin/jira plugin/sftp $PINERY
do
	cd "${ROOT_PATH}/$dir"
	mvn clean install
done
