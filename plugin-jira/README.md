# JIRA Plugin
[JIRA](https://www.atlassian.com/software/jira) is an issue and project tracking
application.
The JIRA plugin for Shesmu can be used to:

- open and close tickets
- search for tickets matching a keyword
- count the number of tickets matching a keyword

To set up a JIRA integration, create a file ending in `.jira` as follows:

      {
        "closeActions": [
          "Close Issue",
          "Resolve Issue"
        ],
        "closedStatues": [
          "CLOSED",
          "RESOLVED"
        ],
        "issueType": "Bug",
        "passwordFile": "/path/to/jira/password",
        "projectKey": "PK",
        "reopenActions": [
          "Reopen Issue"
        ],
        "searches": [],
        "url": "https://jira.example.com",
        "user": "ticketbot"
      }

`closeActions` are the names of the buttons (yes, really) in JIRA that close a
ticket. `reopenActions` are the names of the buttons in JIRA that reopen a
closed ticket. `closedStatuses` are the statuses of tickets that should be
considered closed. Any other status is considered open.

The `url` defines the JIRA server that will be used and `user` is the name of
the user to authenticate as and `passwordFile` is the path of a text file
containing the passsword for this user. One configuration file is needed for
each JIRA project, which is specified in the `projectKey` property. Normally,
the `closeActions`, `reopenActions`, and `closedStatuses` will be the same for
most projects on a JIRA server, but they need not be, hence the need for
separate configuration files.

Shesmu needs to create and reopen tickets, but it can only do so if there is no
mandatory fields to fill in beyond the summary and description. Shesmu allows
setting an assignee on new tickets, but the assignee field must be available in
the _Create Ticket_ window or an error will occur.

The `searches` section allow JIRA tickets to be integrated with Shesmu's action
searches on the _Actions_ page. The idea is meant for the following use case:
if there is a person responsible for fixing failing actions from Shesmu, how
should they delegate those issues? Using searches per the following:


    "searches": [
      {
        "filter": {
          "states": [
            "FAILED",
            "UNKNOWN",
            "HALP"
          ],
          "type": "status"
        },
        "jql": "project IN (\"GC\", \"GDI\") AND resolution = Unresolved",
        "name": "Problems from {key} - {summary} ({assignee})",
        "type": "EACH_AND"
      },
      {
        "filter": {
          "states": [
            "FAILED",
            "UNKNOWN",
            "HALP"
          ],
          "type": "status"
        },
        "jql": "project IN (\"GC\", \"GDI\") AND resolution = Unresolved",
        "name": "Pipeline Lead Dashboard",
        "type": "ALL_EXCEPT"
      },
      {
        "filter": {
          "states": [
            "FAILED",
            "UNKNOWN",
            "HALP"
          ],
          "type": "status"
        },
        "jql": "project IN (\"GC\", \"GDI\") AND resolution = Unresolved",
        "name": "Problems Currently Handed-Off",
        "type": "ALL_AND"
      }
    ],

Each search has a `jql` expression which is used to extract tickets from JIRA.
Any matching tickets have their _descriptions_ scanned for action identifiers
or text-encoded searches (available from the _Action_ and _Olive_ pages by
going to _Export Search_, then _Copy to Clipboard for Ticket_).

These collected searches are combined with the base search `filter` using the
`type`. There are 3 types supported:

- `EACH_AND` creates a search for each ticket by combining the base filter
  and the filters from the ticket. The name can contain `{key}` for the ticket
	ID (_e.g._ JIRA-123), `{summary}` for the summary/title of the ticket, and
  `{assignee}` for the full name of the person assigned to the ticket.
- `ALL_AND` creates one search that matches the base filter and the union of
  the ticket filters.
- `ALL_EXCEPT` creates one search that matches the base filter and none of the
  ticket filters.
- `BY_ASSIGNEE` creates one search that matches the base filter and any of the
	ticket filters that are assigned to a particular person. The name can contain
  `{assignee}` for the full name of the person for the ticket group.

Searches can also be export back to JIRA from Shesmu. By setting the
`"issueType"` property to the name of an issue type for this project, the
_Export Search_ button will have an entry to create a new issue with the
current search already in the description.  If the `"issueType"` is null or not
an issue type present in the project, the button will not appear.
