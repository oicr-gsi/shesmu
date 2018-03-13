#!/bin/sh

set -eu

for dir in shesmu-server action-jira source-pinery
do
	cd $dir
	mvn clean install
	cd ..
done
