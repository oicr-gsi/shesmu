# Maintenance Schedule Editor

Editing maintenance schedules is hard, especially when Excel will break all
the dates. This is a simple, ugly editor for those files.

Build with:

     mvn clean install

Then run the resulting JAR:

     java -jar target/maintenance-editor.jar maintenance.schedule

If no file is specified, an open dialog will be presented. If you must create a
new schedule, create a blank file first.
