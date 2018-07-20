#!/bin/sh

set -eu

case "${JAVA_HOME}" in
	*jdk8)
		PINERY=source-pinery
		;;
	*)
		PINERY=
		;;
esac

for dir in shesmu-server action-jira $PINERY
do
	cd $dir
	mvn clean install
	cd ..
done
