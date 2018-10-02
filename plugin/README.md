# Do more with Shesmu

Shesmu plugins provide additional functionality that builds on the base Shesmu
server capabilities. Shesmu can be configured to launch custom actions, and take
in additional input formats.

## Guanyin
[Guanyin](https://github.com/oicr-gsi/guanyin) is a report-tracking application.
It records which reports have been run, and with what parameters.
The Guanyin plugin for Shesmu can allow Shesmu to launch reports to be run 
through a [DRMAA](http://www.drmaa.org/) web service. Before launching a report
action, Shesmu first checks with Guanyin to see if the report has already been
run.

## JIRA
[JIRA](https://www.atlassian.com/software/jira) is an issue and project tracking
application.
The JIRA plugin for Shesmu can be used to launch additional actions (opening and
closing JIRA tickets). The
[launch-actions.md](https://github.com/oicr-gsi/shesmu/launch-actions.md) file
contains instructions for setting up JIRA integration.

## Nabu
[Nabu](https://github.com/oicr-gsi/nabu) is a web application which tracks the QC
status of files.
The Nabu plugin for Shesmu can set Nabu up as a Shesmu input source.

## Runscanner
[Runscanner](https://github.com/oicr-gsi/runscanner) is an application which scans
directories for data from DNA & RNA sequencing runs.
The Runscanner plugin for Shesmu can set up Runscanner as a Shesmu input source.

## Niassa & Pinery
[Niassa](https://github.com/oicr-gsi/niassa) is a bioinformatics workflow engine
and analysis provenance system.
[Pinery](http://github.com/oicr-gsi/pinery) is a web service application that
provides generalized LIMS\* access for information about samples.
The Niassa & Pinery plugin for Shesmu can set up Niassa and Pinery as Shesmu
input sources.
\*LIMS: Laboratory Information Management System

## SFTP
The SFTP plugin allows Shesmu to get metadata from and check if files exist on 
a remote file system.
