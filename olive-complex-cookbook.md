# Using Olive Features Together
Shesmu has a lot of features that can be stacked to do complicated things
compactly. These are examples not necessarily meant to templates, but general
ideas with a dissection of why.

## Selection of Multiple Output
The goal here is to select the "best" output from a workflow. Older versions of
the workflow produced one file in a unhelpful format. The new one produces two
files: a useful format and the historic unhelpful one. The goal is to pick the
best format for newest run of this workflow.

    Input cerberus_fp;
    Olive
      Where workflow == "The Workflow Of Interest"
        && (metatype == "application/json" || metatype == "application/zip-report-bundle")
      Group
        By ius, workflow_accession
        Into
          outputs = List {accession, path, metatype},
          timestamp = Max timestamp
      Pick Max timestamp By ius
      Let
        ius = ius,
        {accession, path, metatype} = OnlyIf
          For {accession, path, metatype} In outputs:
            Sort (If metatype == "the good format" Then 0 Else 1)
            First {accession, path, metatype}
      Run whatever With
        {run, lane, barcode} = ius,
        accession = accession,
        path = path,
        metatype = metatype;

Here is the clause-by-clause breakdown. First, we select only the workflows and
file types of interest:

      Where workflow == "The Workflow Of Interest"
        && (metatype == "application/json" || metatype == "application/zip-report-bundle")

Then, we group based on each IUS (library that was sequenced) and workflow run:

      Group
        By ius, workflow_accession

And we collect two things from each of these per-workflow-run groups: the
newest file time-stamp and a collection of all the file SWID, path, and file
metatype tuples. This `outputs` list will contain all the files produced by the
workflow; presumably either one (the unhelpful format) or two (the unhelpful
format + the useful one):

        Into
          outputs = List {accession, path, metatype},
          timestamp = Max timestamp

Now, select the latest edition of this workflow run:

      Pick Max timestamp By ius

This is the most complex step which select the best file and throws away the other, if it exists.

      Let
        ius = ius,
        {accession, path, metatype} = OnlyIf For {accession, path, metatype} In outputs:
          Sort (If metatype == "the good format" Then 0 Else 1)
          First {accession, path, metatype}

Let's break this down further. The first part simply reshapes the data preserving the IUS:

      Let
        ius = ius,

We will continue on slightly out of order:

          For {accession, path, metatype} In outputs:

This iterates over all the output we collected. This uses destructuring to get
the contents of the tuple at the time of iteration. We need to find the "best"
file. The plan is to sort the tuples from best to worst and pick the first one:

          For {accession, path, metatype} In outputs:
            Sort (If metatype == "the good format" Then 0 Else 1)
            First {accession, path, metatype}

This uses the numeric sort operation to place the better format before the
worse one (if both exist) and `First` to select whatever item has the best sort
criterion. `First` will emit another tuple. It's in exactly the same format as
the original tuple, so this could be written as:

          For output In outputs:
            Sort (If output[2] == "the good format" Then 0 Else 1)
            First output

with no change in meaning. `First` cannot guarantee that there are any items in
the list at all, so it will return an optional of the tuple. Since we only want
rows where there is one file, we use `OnlyIf`:

        {accession, path, metatype} = OnlyIf
          For {accession, path, metatype} In outputs:
            Sort (If metatype == "the good format" Then 0 Else 1)
            First {accession, path, metatype}

This will discard any rows with no files matching our criteria. At the same
time, it does a destructuring assignment, unpacking the contents of the tuple
in the variables.

Finally, we run the intended action:

      Run whatever With
        {run, lane, barcode} = ius,
        accession = accession,
        path = path,
        metatype = metatype;

This also makes use of destructuring assignment for the IUS. Again, with no
practical different, the ending could have been written:

      Let
        ius = ius,
        output = OnlyIf
          For output In outputs:
            Sort (If output[2] == "the good format" Then 0 Else 1)
            First output
      Run whatever With
        run = ius[0],
        lane = ius[1],
        barcode = ius[2],
        accession = output[0],
        path = output[1],
        metatype = output[2];

or:

      Let
        ius = ius,
        output = OnlyIf
          For output In outputs:
            Sort (If output[2] == "the good format" Then 0 Else 1)
            First output
      Run whatever With
        {run, lane, barcode} = ius,
        {accession, path, metatype} = output;

## Match Checking
Shesmu has three major ways to control program flow: `If`, `Switch` and `For`.
`For` is useful for collections and optionals. `If` and `Switch` are both
useful for decision making.

Since `Switch` works on tuples, it is possible to create decision-making flow that looks more like "matching" the desired data. This is a clause from the MAVIS olive:

    Where
      project In miso_active_projects
      && library_design In ["WG", "WT", "MR"]
      && tissue_type != "R"
      && (Switch {workflow, metatype}
          When {"BamMergePreprocessing", "application/bam"} Then True
          When {"BamMergePreprocessing", "application/bam-index"} Then True
          When {"Manta", "text/vcf"} Then True
          When {"StructuralVariation", "text/vcf"} Then True
          When {"StarFusion", "text/plain"} Then "{path}" ~ /\.abridged\.tsv$/
          Else False)

This initial selection on `project`, `library_design`, and `tissue_type` is
pretty standard. The `Switch` is not. Let's start with an equivalent logical
expression and refactor it into the above:

    Where
      project In miso_active_projects
      && library_design In ["WG", "WT", "MR"]
      && tissue_type != "R"
      && (
          workflow == "BamMergePreprocessing" && (metatype == "application/bam"
                                                  || "application/bam-index") ||
          metatype == "text/vcf" && (workflow == "Manta"  ||
                                     workflow == "StructuralVariation") ||
          worfklow == "StarFusion" && metatype == "text/plain" && "{path}" ~ /\.abridged\.tsv$/)

This check is very ragged: in the case of `BamMergePreprocessing`, we care
about different metatypes while for the metatype `text/vcf`, we care about
different workflows. Let's distribute those conditions. This will mean
redundant checks, but it will be clearer what the combinations are:

    Where
      project In miso_active_projects
      && library_design In ["WG", "WT", "MR"]
      && tissue_type != "R"
      && (
          workflow == "BamMergePreprocessing" && metatype == "application/bam" ||
          workflow == "BamMergePreprocessing" && metatype == "application/bam-index" ||
          workflow == "Manta" && metatype == "text/vcf" ||
          workflow == "StructuralVariation" && metatype ==  "text/vcf" ||
          worfklow == "StarFusion" && metatype == "text/plain" && "{path}" ~ /\.abridged\.tsv$/)

Now, all of our conditions have a check for `workflow` and `metatype` where the
last one has an extra check. Rather than compare two things, lets put them into
tuples and compare the tuples:

    Where
      project In miso_active_projects
      && library_design In ["WG", "WT", "MR"]
      && tissue_type != "R"
      && (
          {workflow, metatype} == {"BamMergePreprocessing", "application/bam"} ||
          {workflow, metatype} == {"BamMergePreprocessing", "application/bam-index"} ||
          {workflow, metatype} == {"Manta", "text/vcf"} ||
          {workflow, metatype} == {"StructuralVariation", "text/vcf"} ||
          {worfklow, metatype} == {"StarFusion", "text/plain"} && "{path}" ~ /\.abridged\.tsv$/)

Since `{workflow, metatype}` is now in every condition, we can factor it out into the `Switch`:

    Where
      project In miso_active_projects
      && library_design In ["WG", "WT", "MR"]
      && tissue_type != "R"
      && (Switch {workflow, metatype}
          When {"BamMergePreprocessing", "application/bam"} Then True
          When {"BamMergePreprocessing", "application/bam-index"} Then True
          When {"Manta", "text/vcf"} Then True
          When {"StructuralVariation", "text/vcf"} Then True
          When {"StarFusion", "text/plain"} Then "{path}" ~ /\.abridged\.tsv$/
          Else False)

For most of the conditions, everything is taken care of by the `Switch` to the
result is simply `True`. For the `StarFusion` case, the extra conditions
becomes the `Then`.

Here is another case of this from `bcl2barcode`. It manipulates the bases mask
to replace the first `Y`_n_ with `Y1N*`, and any subsequent `Y` with `N` while
leaving the first two `I` untouched.

    Function modify_bases_mask(masks_type bases_mask_parts)
       pack_bases_mask(
         For mask In bases_mask_parts:
           Flatten (new_mask In
             Switch {mask.type, mask.ordinal}
               When {"Y", 0} Then
                 [ { type = "Y", position = 0, group = mask.group, length = 1 },
                   { type = "N", position = 1, group = mask.group, length = -1 } ]
               When {"I", 0} Then
                 [ convert_mask(mask) ]
               When {"I", 1} Then
                 [ convert_mask(mask) ]
               When {"Y", 1} Then
                 [ { type = "N", position = 0, group = mask.group, length = -1 } ]
               Else
                 # unsupported bases_mask!
                 [ convert_mask(mask) ]
               )
         List new_mask);

The `bases_mask_parts` is a list of objects already parsed by another function.
`pack_bases_mask` creates a new bases mask from the objects provided. Since the
parser provides extra information the packer doesn't need `convert_mask`, drops
the superfluous data.

This code takes each mask and does a `Flatten` on it. The mapping between old
input masks is usually 1:1, but in the case where `Y`_n_ maps to `Y1N*`, it is
1:2, so the `Flatten` will allow collecting the two. Again, this uses a
`Switch` on a tuple. The tuple is the mask type (`Y`, `I`, `N`) and the ordinal
(the number of this type appears in the sequence. So, `{"Y", 0}` is the first
read, `{"I", 0}` is the first index, `{"I", 1}` is the second index, and `{"Y",
1}` is the second read. This assumes a relatively normal read structure for the
sequencer. If other reads or indices were common, it might make sense to have
the `Else` convert them to an `N*` sequence as the `{"Y", 1}` case does.
