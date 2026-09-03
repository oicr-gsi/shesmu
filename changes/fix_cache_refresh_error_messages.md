Uninformative error messages when a cache failed to refresh
* the whole exception cause chain is now reported instead of only the outermost message, which was frequently something as useless on its own as `closed`
* a JSON list that stops part way through now reports how many records and how many bytes were read before it failed, which distinguishes a response that was cut short from malformed JSON
