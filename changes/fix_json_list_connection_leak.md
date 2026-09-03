HTTP connections leaked when reading a streamed JSON list did not run to completion
* the response body is now closed if parsing fails part way through, or if the stream is abandoned before the end of the array
* `ReplacingRecord` and `MergingRecord` now close their stream when collecting it fails, rather than only on success
* creating the parser reads from the body to determine the encoding, so a response that was already dead failed before there was any stream for the caller to close; the body is now released in that case too
* Pinery now closes the sequencer runs response rather than relying on reaching the end of the array to do it, which did not happen if collecting the runs failed
