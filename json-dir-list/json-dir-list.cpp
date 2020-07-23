#include <cstring>
#include <deque>
#include <dirent.h>
#include <fstream>
#include <grp.h>
#include <iostream>
#include <json/json.h>
#include <pwd.h>
#include <sstream>
#include <sys/stat.h>
#include <unistd.h>

// Convert a timespec to an approximate floating point value of second since
// the epoch; this is a terrible format, but it's what `find` gives us, so
// we're going with it.
static double to_seconds(timespec ts) {
  return (double)ts.tv_sec + (ts.tv_nsec / 1E9);
}

int main(int argc, const char **argv) {
  // Figure out the hostname to report in the records we return
  std::string host("unknown");
  char hostname[1024];
  if (gethostname(hostname, sizeof hostname) == 0) {
    host = hostname;
  }

  // Populate our queue with the directories provided by the user
  std::deque<std::string> roots;
  for (auto i = 1; i < argc; i++) {
    roots.push_back(argv[i]);
  }

  // The JSON library can't do streaming writes, so we're going to write the
  // array manually, and write the objects using the library.
  auto first = true;
  std::cout << "[";
  while (!roots.empty()) {
    DIR *dir = nullptr;
    errno = 0;
    dir = opendir(roots.front().c_str());
    if (dir == nullptr) {
      // We're just going to keep going if we encounter errors reading a
      // directory
      perror("opendir");
      roots.pop_front();
      continue;
    } else {
      struct dirent *entry = nullptr;
      while ((entry = readdir(dir))) {
        if (strcmp(entry->d_name, ".") == 0 ||
            strcmp(entry->d_name, "..") == 0) {
          continue;
        }
        std::stringstream path;
        path << roots.front() << "/" << entry->d_name;

        if (entry->d_type == DT_DIR) {
          // Any child directories should be explored later
          roots.push_back(path.str());
        } else {
          // For anything that's a file (or pipe or symlink or whatever), write
          // out a record
          Json::Value record(Json::objectValue);
          struct stat info = {0};
          if (stat(path.str().c_str(), &info) != 0) {
            continue;
          }
          struct passwd *pw = getpwuid(info.st_uid);
          struct group *gr = getgrgid(info.st_gid);
          record["file"] = path.str();
          record["size"] = (Json::LargestInt)info.st_size;
          record["atime"] = to_seconds(info.st_atim);
          record["ctime"] = to_seconds(info.st_ctim);
          record["mtime"] = to_seconds(info.st_mtim);
          record["user"] = pw->pw_name;
          record["group"] = gr->gr_name;
          record["perms"] = (int)info.st_mode;
          record["host"] = host;

          if (first) {
            first = false;
          } else {
            std::cout << ",";
          }
          std::cout << record;
        }
      }
    }
    closedir(dir);
    roots.pop_front();
  }
  std::cout << "]";

  return 0;
}
