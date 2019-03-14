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
        "passwordFile": "/path/to/jira/password",
        "projectKey": "PK",
        "reopenActions": [
          "Reopen Issue"
        ],
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
