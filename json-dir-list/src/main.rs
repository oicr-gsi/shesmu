use serde::ser::SerializeSeq;
use serde::{Serialize, Serializer};
use std::collections::VecDeque;
use std::path::PathBuf;

#[derive(Serialize)]
struct UnixFileData<'a> {
    atime: f64,

    ctime: f64,

    fetched: &'a str,

    file: &'a str,

    group: &'a str,

    host: &'a str,

    mtime: f64,

    perms: u32,

    size: u64,

    user: &'a str,
}
#[cfg(unix)]
fn write_record(
    output: &mut impl serde::ser::SerializeSeq,
    host: &str,
    fetched: &str,
    entry: &std::fs::DirEntry,
    metadata: &impl std::os::unix::fs::MetadataExt,
) {
    // Convert a timespec to an approximate floating point value of second since
    // the epoch; this is a terrible format, but it's what `find` gives us, so
    // we're going with it.
    fn to_seconds(time: i64, time_ns: i64) -> f64 {
        (time as f64) + (time_ns as f64) / 1e9
    }

    let Some(user) = users::get_user_by_uid(metadata.uid()) else {
        return;
    };
    let Some(group) = users::get_group_by_gid(metadata.gid()) else {
        return;
    };
    output
        .serialize_element(&UnixFileData {
            atime: to_seconds(metadata.atime(), metadata.atime_nsec()),
            ctime: to_seconds(metadata.ctime(), metadata.ctime_nsec()),
            fetched,
            file: entry.path().to_str().expect("File name is not Unicode"),
            group: group.name().to_str().unwrap_or(""),
            host,
            mtime: to_seconds(metadata.mtime(), metadata.mtime_nsec()),
            perms: metadata.mode(),
            size: metadata.size(),
            user: user.name().to_str().unwrap_or(""),
        })
        .expect("Failed to write entry");
}

#[cfg(windows)]
fn write_record(
    output: &mut impl serde::ser::SerializeSeq,
    host: &str,
    fetched: &str,
    entry: &std::fs::DirEntry,
    metadata: &impl std::os::windows::fs::MetadataExt,
) {
    // Windows timespecs are 100ns ticks from January 1, 1601
    fn to_seconds(time: u64) -> f64 {
        if time == 0 {
            // The underlying file system doesn't know, so use a convenient lie
            0.0
        } else {
            (time as f64) / 100e9 - 11_643_609_600f64
        }
    }
    output
        .serialize_element(&UnixFileData {
            atime: to_seconds(metadata.last_access_time()),
            ctime: to_seconds(metadata.creation_time()),
            fetched,
            file: entry.path().to_str().expect("File name is not Unicode"),
            group: "",
            host,
            mtime: to_seconds(metadata.last_write_time()),
            perms: 0644,
            size: metadata.file_size(),
            user: "",
        })
        .expect("Failed to write entry");
}

pub fn main() {
    let hostname = gethostname::gethostname()
        .into_string()
        .expect("Host name is not valid Unicode");

    let mut roots: VecDeque<PathBuf> = std::env::args().map(|dir| PathBuf::from(dir)).collect();

    let fetched = chrono::Utc::now().to_rfc3339();

    let mut serializer = serde_json::Serializer::new(std::io::stdout());
    let mut output = serializer.serialize_seq(None).expect("Failed to write");

    while let Some(directory) = roots.pop_back() {
        // We're just going to keep going if we encounter errors reading a
        // directory
        let Ok(reader) = std::fs::read_dir(&directory) else {
            continue;
        };
        for entry in reader {
            let Ok(entry) = entry else { continue };
            if entry.file_name() == "." || entry.file_name() == ".." {
                continue;
            }
            let Ok(metadata) = entry.metadata() else {
                continue;
            };
            if metadata.is_dir() {
                // Any child directories should be explored later
                roots.push_back(entry.path())
            } else {
                write_record(&mut output, &hostname, &fetched, &entry, &metadata);
            }
        }
    }
    output.end().expect("Failed to write");
}
