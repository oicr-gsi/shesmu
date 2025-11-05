Add optional `canSubmit` field to Vidarr configuration to toggle whether Shesmu 
can build Vidarr workflow actions.
If the field is absent, Shesmu will be able to build and submit actions to the
Vidarr instance.
If the field is set to `false`, Shesmu will only build caches from the Vidarr 
instance.
