package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.files.FileWatcher;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.IOException;
import java.text.ParseException;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;

@DisabledIfSystemProperty(named = "skipIT", matches = "true")
public class UserInterfaceTest {

  private final String baseUrl = "http://localhost:" + System.getProperty("shesmu.server.port");
  private static WebDriver driver;
  private static Server server;

  @BeforeAll
  public static void setup() throws IOException, ParseException {
    TimeZone.setDefault(TimeZone.getTimeZone("Canada/Eastern"));
    WebDriverManager.chromedriver().setup();
    server =
        new Server(
            Integer.parseInt(System.getProperty("shesmu.server.port")),
            FileWatcher.of(Stream.of(UserInterfaceTest.class.getResource("/ui-data").getPath())));
    server.start();
  }

  public UserInterfaceTest() {

    final var opts = new ChromeOptions();
    opts.setHeadless(true);
    opts.addArguments("--window-size=1920,1080");
    // I would love to tell you what these do, but without them, you get a nonspecific ChromeDriver
    // error under Docker.
    // https://stackoverflow.com/questions/50642308/webdriverexception-unknown-error-devtoolsactiveport-file-doesnt-exist-while-t/50642913#50642913
    opts.addArguments("start-maximized"); // open Browser in maximized mode
    opts.addArguments("disable-infobars"); // disabling infobars
    opts.addArguments("--disable-extensions"); // disabling extensions
    opts.addArguments("--disable-gpu"); // applicable to windows os only
    opts.addArguments("--disable-dev-shm-usage"); // overcome limited resource problems
    opts.addArguments("--no-sandbox"); // Bypass OS security model
    final var loggingPrefs = new LoggingPreferences();
    loggingPrefs.enable(LogType.BROWSER, Level.ALL);
    opts.setCapability(CapabilityType.LOGGING_PREFS, loggingPrefs);
    driver = new ChromeDriver(opts);

    // don't allow page load or script execution to take longer than 10 seconds
    driver.manage().timeouts().pageLoadTimeout(15, TimeUnit.SECONDS);
    driver.manage().timeouts().setScriptTimeout(15, TimeUnit.SECONDS);
  }

  @Test
  public void test001Status() {
    driver.get(baseUrl);
  }

  @Test
  public void test002Metrics() {
    driver.get(baseUrl + "/metrics");
  }
}
