use clap::Parser;
use std::collections::BTreeMap;
use std::fmt::{Debug, Display, Formatter};

#[derive(Parser, Debug)]
#[command(
    author = "Andre Masella",
    version,
    about = "Extract data from a Shesmu instance",
    long_about = "Downloads data from a Shesmu instance in tabular format using AWK-like queries"
)]
struct Args {
    #[arg(short, long, help = "The configuration file to use", default_value_t = config_file())]
    config: String,
    #[arg(short, long, help = "The Shesmu input format to extract")]
    input: Option<String>,
    #[arg(short, long, help = "The output format to generate")]
    format: Option<String>,
    #[arg(
        short,
        long,
        help = "The output file to write the contents instead of standard output"
    )]
    output: Option<String>,
    #[arg(
        short = 'H',
        long,
        help = "The Shesmu instance (or alias) to run the query"
    )]
    host: Option<String>,
    #[arg(
        long,
        help = "Waits for new data from the Shesmu instance rather than using cached data if stale."
    )]
    wait_for_fresh: bool,

    #[arg(help = "The query to execute")]
    query: String,
}
#[derive(serde::Deserialize, Default)]
struct Configuration {
    aliases: Option<BTreeMap<String, String>>,
    default_host: Option<String>,
    default_input_format: Option<String>,
    default_output_format: Option<String>,
    prepared_columns: Option<BTreeMap<String, String>>,
}
enum ErrorMessage {
    Fixed(&'static str),
    Reqwest(reqwest::Error),
    Yaml(serde_yaml::Error),
}
#[derive(serde::Serialize)]
#[allow(non_snake_case)]
struct ExtractRequest<'a> {
    inputFormat: &'a str,
    outputFormat: &'a str,
    preparedColumns: Option<&'a BTreeMap<String, String>>,
    query: &'a str,
    readStale: bool,
}
fn config_file() -> String {
    let project_dirs = directories::ProjectDirs::from("", "OICR", "shawk")
        .expect("Unable to determine configuration file path");
    project_dirs
        .config_dir()
        .to_path_buf()
        .join("shawk.yaml")
        .to_str()
        .expect("Failed to convert path to string")
        .to_string()
}
fn run() -> Result<bool, ErrorMessage> {
    {
        let args = Args::parse();

        let config: Configuration = match std::fs::File::open(&args.config) {
            Ok(file) => serde_yaml::from_reader(file)?,
            Err(_) => Default::default(),
        };

        let instance = args
            .host
            .as_ref()
            .or(config.default_host.as_ref())
            .ok_or("A Shesmu host must be specified with `-H`.")?;
        let url = reqwest::Url::parse(
            config
                .aliases
                .as_ref()
                .map(|a| a.get(instance))
                .flatten()
                .unwrap_or(instance),
        )
        .expect("Invalid host URL")
        .join("extract")
        .expect("Could not add endpoint to URL");

        let basic_auth = if !url.username().is_empty() && url.password().is_none() {
            Some((
                url.username().to_string(),
                rpassword::prompt_password(&format!("Password for {}: ", url.authority()))
                    .expect("Failed to get password."),
            ))
        } else {
            None
        };

        let client = reqwest::blocking::Client::new();
        let request = client.post(url).json(&ExtractRequest {
            inputFormat: args
                .input
                .as_ref()
                .or(config.default_input_format.as_ref())
                .ok_or("An input format must be specified with `-i`.")?,
            outputFormat: args
                .format
                .as_ref()
                .or(config.default_output_format.as_ref())
                .ok_or("An output format must be specified with `-f`.")?,
            preparedColumns: config.prepared_columns.as_ref(),
            query: &args.query,
            readStale: args.wait_for_fresh,
        });

        let mut result = match basic_auth {
            None => request,
            Some((username, password)) => request.basic_auth(username, Some(password)),
        }
        .send()?;
        if result.status().is_success() {
            match args.output.as_ref() {
                None => result.copy_to(&mut std::io::stdout()),
                Some(file_name) => {
                    let mut output_file =
                        std::fs::File::create(file_name).expect("Failed to open output file");
                    result.copy_to(&mut output_file)
                }
            }?;
            Ok(true)
        } else {
            result.copy_to(&mut std::io::stderr())?;
            Ok(false)
        }
    }
}
fn main() {
    std::process::exit(match run() {
        Err(e) => {
            eprintln!("Failed: {}", e);
            1
        }
        Ok(true) => 0,
        Ok(false) => 1,
    });
}
impl Display for ErrorMessage {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            ErrorMessage::Fixed(m) => f.write_str(m),
            ErrorMessage::Reqwest(r) => Display::fmt(r, f),
            ErrorMessage::Yaml(y) => Display::fmt(y, f),
        }
    }
}
impl From<&'static str> for ErrorMessage {
    fn from(value: &'static str) -> Self {
        ErrorMessage::Fixed(value)
    }
}
impl From<reqwest::Error> for ErrorMessage {
    fn from(value: reqwest::Error) -> Self {
        ErrorMessage::Reqwest(value)
    }
}
impl From<serde_yaml::Error> for ErrorMessage {
    fn from(value: serde_yaml::Error) -> Self {
        ErrorMessage::Yaml(value)
    }
}
