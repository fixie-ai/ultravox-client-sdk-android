package ai.ultravox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(JUnit4.class)
public class ClientVersionCheckTest {
  private static final Pattern VERSION_LINE_PATTERN =
      Pattern.compile("^\\s+version = \"(?<version>[0-9.]+)\"\\s*$");

  @Test
  public void checkSdkVersion_matchesGradle() throws Exception {
    Path path = FileSystems.getDefault().getPath("").resolveSibling("build.gradle.kts");
    String gradleSdkVersion = null;
    for (String line : Files.readAllLines(path)) {
      Matcher matcher = VERSION_LINE_PATTERN.matcher(line);
      if (matcher.matches()) {
        gradleSdkVersion = matcher.group("version");
        break;
      }
    }
    assertNotNull("Failed to find SDK version from Gradle", gradleSdkVersion);
    assertEquals(gradleSdkVersion, UltravoxSession.ULTRAVOX_SDK_VERSION);
  }
}
