# BED and FAI File Plugin

## BED Interval Files
This plugin allows converting a directory of BED files for specific targeted sequencing
panels into a lookup structure that contain path and chromosome information. A file ending
`.intervalbed` described below points to a separate directory of BED files.

    {
       "directory": "panels",
       "replacementPrefix": "/srv/panels"
    }

The `"directory"` property defines a path, relative to the configuration file, to scan for BED files.
The `"replacementPrefix"` defines a new prefix that should be used for paths given to the olives.
That is, suppose this file lives in `/srv/shesmu/config`, then Shesmu will
scan `/srv/shesmu/config/panels` for BED files and for a BED file `ALL.WG.hg38.bed`, it will be
given to the olive as `/srv/panels/ALL.WG.hg38.bed`.

The panels must have the format *panel*`.`*library-type*`.`*genome*`.bed`. The _genome_ can be any
characters except `.`. The _library-type_ can be any uppercase characters. Multiple library types
can be specified, separated by commas (_e.g._, `foo.EX,WG.hg19.bed`) The _panel_ can be any
characters except `.`, but `ALL` is treated specially.

When an olive performs a lookup, it must provide the panel, library type, and genome. First, an
exact match is attempted (_e.g._, `::get("TS", "mm10", "IDT Mickey Mouse"` will look
for `IDT Mickey Mouse.TS.mm10.bed`). If no exact match is found, then it will fall back on the `ALL`
file, if available (_e.g._, `::get("WG", "mm10", "IDT Mickey Mouse"` will look
for `IDT Mickey Mouse.WG.mm10.bed` first, then `ALL.WG.mm10.bed`).

Sometimes a panel has multiple names or the name contains characters that should not be in files
names (_.e.g_, `/`, `.`). In that case, a second file with the extension `.alias` can be provided
with alternate panel names, one per line. Blank lines are ignored.

## FAI Sorting Plugin
This plugin allows reading FASTA index files (`.fai`) for a genome build and
creating a sort index. A directory should contain all the `.fai` files and then
a `.genomeidx` file as follows:

    {
       "directory": "genome-indicies"
    }

The `"directory"` property defines a path, relative to the configuration file,
to scan for FAI files. It will export a function to olives called `sort_order`
that can be used like this in an olive:

    For chromosome In chromosomes:
        Sort genomeidx::foo::sort_order("hg19", chromosome)
        Where is_useful_for_workflow(chromosome)
        FixedConcat chromosome With ","

to generate a string of chromosome names in the same order as the FASTA file.
