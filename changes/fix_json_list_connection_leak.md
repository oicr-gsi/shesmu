HTTP connections leaked when reading a streamed JSON list did not run to completion
* the response body is now closed if parsing fails part way through, or if the stream is abandoned before the end of the array
* `ReplacingRecord` and `MergingRecord` now close their stream when collecting it fails, rather than only on success
