Uninformative error messages when a cache failed to refresh
* the whole exception cause chain is now reported instead of only the outermost message, which was frequently something as useless on its own as `closed`
* a JSON list that stops part way through now reports how many records and how many bytes were read before it failed, which distinguishes a response that was cut short from malformed JSON
* each message in the chain is flattened onto one line and abbreviated, since Jackson's run to several lines and append a source location naming the features that were disabled while producing it
* an `Error`, such as running out of memory, is no longer wrapped up as an ordinary failed refresh and swallowed by the cache machinery
