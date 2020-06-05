package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.files.FileWatcher;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.IOException;
import java.text.ParseException;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;

public class UserInterfaceTest {

  private final String baseUrl = "http://localhost:" + System.getProperty("shesmu.server.port");
  private static WebDriver driver;
  private static Server server;

  @BeforeClass
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

    final ChromeOptions opts = new ChromeOptions();
    opts.setHeadless(true);
    opts.addArguments("--disable-gpu");
    final LoggingPreferences loggingPrefs = new LoggingPreferences();
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
