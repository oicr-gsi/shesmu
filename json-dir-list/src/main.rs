use serde::ser::SerializeSeq;
use serde::{Serialize, Serializer};
use std::collections::VecDeque;
use std::os::linux::fs::MetadataExt;
use std::os::unix::fs::PermissionsExt;
use std::path::PathBuf;
use std::time::SystemTime;

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
// Convert a timespec to an approximate floating point value of second since
// the epoch; this is a terrible format, but it's what `find` gives us, so
// we're going with it.
fn to_seconds(time: std::io::Result<SystemTime>) -> f64 {
    match time {
        Err(_) => 0.0,
        Ok(time) => {
            let (duration, multiplier) = match time.duration_since(SystemTime::UNIX_EPOCH) {
                Err(err) => (err.duration(), -1.0),
                Ok(duration) => (duration, 1.0),
            };
            duration.as_secs_f64() * multiplier
        }
    }
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
            let file = entry.path();
            let Ok(metadata) = entry.metadata() else {
                continue;
            };
            if metadata.is_dir() {
                // Any child directories should be explored later
                roots.push_back(file)
            } else {
                let Some(user) = users::get_user_by_uid(metadata.st_uid()) else {
                    continue;
                };
                let Some(group) = users::get_group_by_gid(metadata.st_gid()) else {
                    continue;
                };
                output
                    .serialize_element(&UnixFileData {
                        atime: to_seconds(metadata.accessed()),
                        ctime: to_seconds(metadata.created()),
                        fetched: &fetched,
                        file: file.to_str().expect("File name is not Unicode"),
                        group: group.name().to_str().unwrap_or(""),
                        host: &hostname,
                        mtime: to_seconds(metadata.modified()),
                        perms: metadata.permissions().mode(),
                        size: metadata.len(),
                        user: user.name().to_str().unwrap_or(""),
                    })
                    .expect("Failed to write entry");
            }
        }
    }
    output.end().expect("Failed to write");
}
