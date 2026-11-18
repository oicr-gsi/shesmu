Remote files (_e.g._, `gs://bucket/file`) keep their full URI: these are now `PATH{path} | URI{host = string, name = string, scheme = string}` instead of `path`
* `cerberus_fp` and `cerberus_fp_skipped` `path`
* `vidarr_analysis` `output_files` `path`, and the Vidarr load and import `analysis` `path`
* `nabu_file_qc` `file_path` (records with remote files no longer stop all file QC records from loading)
* `guanyin_report` `files_in`
