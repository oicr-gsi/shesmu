# JSON Directory Listing Tool
For the `unix_file` input format, supported by the SSH plugin, Shesmu will try
to crawl the directory structure using a `find` command to produce JSON output.
This works about as well as one could hope. If file names, user names, or group
names have characters that are now allowed in JSON strings, it does not go
well. This tool provides a more robust alternative that does the JSON encoding
correctly. To install it:

    cargo install --path .

Once installed, in the `.sftp` configuration for Shesmu, the `"listCommand"`
can be set to `"/install/dir/bin/json-dir-list"` to use this command instead.
