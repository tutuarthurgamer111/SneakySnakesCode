import java.io.File;
import java.net.URISyntaxException;

/** Paths used by the installed desktop application. */
public final class AppPaths {
    private AppPaths() {}

    public static File dataDirectory() {
        String roaming = System.getenv("APPDATA");
        File base = (roaming == null || roaming.isBlank())
            ? new File(System.getProperty("user.home"))
            : new File(roaming);
        File dir = new File(base, "SneakySnakes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File dataFile(String name) {
        return new File(dataDirectory(), name);
    }

    /** Resources are installed beside the app-image, not in the user's working directory. */
    public static File resourceFile(String relativePath) {
        try {
            File jarOrClasses = new File(SneakySnakes.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            File appDir = jarOrClasses.isDirectory() ? jarOrClasses : jarOrClasses.getParentFile();
            File installDir = appDir.getParentFile();
            return new File(new File(installDir, "SneakySnakes/Resources"), relativePath);
        } catch (Exception e) {
            return new File("SneakySnakes/Resources", relativePath);
        }
    }
}
