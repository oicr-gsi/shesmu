Errors when a JSON list response is not a list
* an empty response body is now reported as such instead of throwing a `NullPointerException`
* the token named in the error is now the unexpected one rather than the one following it
* a response that is not JSON at all, such as an error page from a proxy, is now reported as such instead of as a complaint about a stray character
