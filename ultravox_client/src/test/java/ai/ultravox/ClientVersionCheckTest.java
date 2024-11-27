package ai.ultravox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(JUnit4.class)
public class ClientVersionCheckTest {
    private static final Pattern VERSION_LINE_PATTERN = Pattern.compile("^\\s+version = \"(?<version>[0-9.]+)\"\\s*$");

    @Test
    public void checkSdkVersion_matchesGradle() throws Exception {
        Path path = FileSystems.getDefault().getPath("");
        path = path.resolveSibling("build.gradle.kts");
        List<String> lines = Files.readAllLines(path);
        String gradleSdkVersion = null;
        for (String line : lines) {
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
