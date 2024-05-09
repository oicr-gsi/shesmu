# Static Actions
While Shesmu is mostly about the olives, it _does_ run actions and it can be
convenient to do that directly. These can be actions generated manually or
using the olive simulator.

To use the simulator:
- Go to _Tools_, _Olive Simulator_ and write a script to generate the desired actions
- From the _Actions_ tab, click _Download_ to save the resulting actions.
- Place this file, ending in `.actnow` into Shesmu's configuration directory.

These new actions will be visible in the _Actions_ dashboard. Since they aren't
connected to any olive, they will not be on the _Olives_ page. They will be
listed as coming from the `.actnow` file and the _Olive Source_ filter on the
_Actions_ page can find all actions from that file.

Files can contain different kinds of actions.

The actions in the file are fixed. If they need to be updated in any way, the
file can be replaced or edited to include new actions, but Shesmu will never
modify the file.

If it is desirable to generate the file manually (or edit the output from the
simulator), the `.actnow` file is a JSON array of the actions to launch. Each
action has the following format: 

    {
      "name": "action_name",
      "parameters": {
         parameter values
      }
    }

The `"name"` is the fully qualified name for the action (_i.e._, the name as it
appears on the _Definitions_ → _Actions_ page) and the parameters are encoded
in the [standard Shesmu way](implementation.md#json).
