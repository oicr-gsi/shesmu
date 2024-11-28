* Adds a timeout for SSH refillers
* Changes the SSH refiller syntax to use the environment variable `SHESMU_REFILLER_HASH` instead of a command line argument. Use `jq '.refillers = (.refillers[] | .command += " $SHESMU_REFILLER_HASH")'` to restore the command line argument. This change can be safely applied before upgrading since it will be a no-op in the previous version.
