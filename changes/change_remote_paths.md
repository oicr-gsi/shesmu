The types of the following input format fields and Vidarr action parameters have changed from `path` to algebraic `PATH{path} | URI{scheme = string, host = string, name = string}` to support remote files (_e.g._, `gs://bucket/file`):
* `cerberus_fp` and `cerberus_fp_skipped` `path`
* `vidarr_analysis` `output_files` `path`, and the Vidarr load and import `analysis` `path`
* `nabu_file_qc` `file_path` (records with remote files no longer stop all file QC records from loading)
* `guanyin_report` `files_in`
