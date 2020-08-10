# Unreleased

# [1.2.0] - 2020-08-10T15:05+00:00

Changes:
 * Create a Check pragma
 * Create intersection join operations
 * Add `min`, `max` and `clamp` functions
 * Ignore unknown fields on source locations in REST API
 * Allow converting advanced searches back into basic
 * Make exported constants available to script checker

Niassa/Pinery Plugin
 * Add `run_lane_count` to `pinery_ius`
 * Allow bases masks like `y51` to be parsed

Run Scanner plugin:
 * Expose RunScanner flowcell geometry functions

JIRA plugin:
 * Add a comment when reopening tickets

SFTP plugin:
 * Add a fetched date to `unix_file`
 * Create a native program to scan directories over SFTP

# [1.1.0] - 2020-07-23T17:32+00:00

UI:
* Fix bug with advanced search
  The not-equals/in flag from the queries in the advanced search was being
  incorrectly disregarded.
* Fix event listener on advanced searches
* Fix bug where hidden histograms aren't rendered
* Fix filter type in crosstab cells
* Upgrade to advanced search when basic won't do
* Fix popup menu calculations

Niassa/Pinery plugin:
* Expose Pinery provider in `pinery_project` source
* Fix HALP state for fixable actions
  Actions that are fixable (updatable by signatures) should transition to the
  match's state rather than HALP.
* Fix comparison when getting workflow SWID for logging

RunScanner plugin:

* Fix incorrect splitting when Run Scanner returns an error
  The Run Scanner plugin makes the assumption that if it fails to fetch the
  flowcell geometry, it can return an empty list and downstream processes will
  consider this an error state. The lane splitting grouper however, did not
  reject such records. This change rejects them.


# [1.0.5] - 2020-07-16T17:03+00:00

Changes:
  * Fix problem in _Actions_ dashboard causing 400s using server searches
  * Fix bug causing saved search to default to _All Actions_ on _Actions_ dashboard

# [1.0.3] - 2020-07-15T21:19+00:00

Changes:
  * Remove deployment to non-functional GitHub Packages Maven

# [1.0.1] - 2020-07-15T21:06+00:00

Changes:
  * Fix bug in Docker build process

# [1.0.0] - 2020-07-15T20:42+00:00

First official release

Changes:
  * Fix LIMS key locking issues in Niassa plugin
  * Create a demo configuration
  * Add date-from-integer library functions
  * Fix incorrect start up time on status page
  * Misc UI fixes and improvements

# [0.0.4] - 2020-07-14T18:39+00:00

Changes:

 * None (developing build process)

# [0.0.2] - 2020-07-14T18:36+00:00

Changes:

  * Start of new release process
