# Define an empty variable for list termination
NULL = 
ACLOCAL_AMFLAGS = -I m4

# Build the following binaries and put the in $prefix/bin
bin_PROGRAMS = \
	json-dir-list \
	$(NULL)
# Install the following manpages in $prefix/share/man/man1
man1_MANS = \
	json-dir-list.1 \
	$(NULL)

# These are all the source .cpp files for. Note the change of `-` to `_`.
json_dir_list_SOURCES = \
	json-dir-list.cpp \
	$(NULL)
# These are the compiler arguments for building
# We want (in order):
# - C++11
# - debugging symbols to be included
# - optimisation level 2
# - whatever junk jsoncpp wants
json_dir_list_CPPFLAGS = \
	-std=c++11 -g -O2 \
	$(JSON_CFLAGS) \
	$(NULL)
# These are the linker flags for building
# We need:
# - whatever junk jsoncpp needs
json_dir_list_LDADD = \
	$(JSON_LIBS) \
	$(NULL)
