package org.apache.maven.wrapper;


import org.apache.maven.wrapper.cli.CommandLineParser;
import org.apache.maven.wrapper.cli.SystemPropertiesCommandLineConverter;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class MavenWrapperMain {
    public static final String DEFAULT_MAVEN_USER_HOME = System.getProperty("user.home") + "/.m2";

    public static final String MAVEN_USER_HOME_PROPERTY_KEY = "maven.user.home";

    public static final String MAVEN_USER_HOME_ENV_KEY = "MAVEN_USER_HOME";

    public static final String MVNW_VERBOSE = "MVNW_VERBOSE";

    public static File SC_DOCS = new File(System.getProperty("java.io.tmpdir") + "/sc_docs");

    public static String SC_DOCS_MAVEN_PROPERTIES = SC_DOCS + "/maven-wrapper.properties";

    public static void main(String[] args) throws Exception {

        if (!SC_DOCS.exists()) {
            SC_DOCS.mkdirs();
        }
        try (FileWriter writer = new FileWriter(SC_DOCS_MAVEN_PROPERTIES)) {
            writer.write("distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.zip");
        }

        String wrapperVersion = wrapperVersion();
        Logger.info("Takari Maven Wrapper " + wrapperVersion);

        Properties systemProperties = System.getProperties();
        systemProperties.putAll(parseSystemPropertiesFromArgs(args));

        addSystemProperties(new File(System.getProperty("maven.multiModuleProjectDirectory")));

        WrapperExecutor wrapperExecutor = WrapperExecutor.forWrapperPropertiesFile(new File(SC_DOCS_MAVEN_PROPERTIES), System.out);
        wrapperExecutor.execute(args, new Installer(new DefaultDownloader("mvnw", wrapperVersion), new PathAssembler(mavenUserHome())), new BootstrapMainStarter());
    }

    private static Map<String, String> parseSystemPropertiesFromArgs(String[] args) {
        SystemPropertiesCommandLineConverter converter = new SystemPropertiesCommandLineConverter();
        CommandLineParser commandLineParser = new CommandLineParser();
        converter.configure(commandLineParser);
        commandLineParser.allowUnknownOptions();
        return converter.convert(commandLineParser.parse(args));
    }

    private static void addSystemProperties(File rootDir) {
        System.getProperties().putAll(SystemPropertiesHandler.getSystemProperties(new File(mavenUserHome(), "maven.properties")));
        System.getProperties().putAll(SystemPropertiesHandler.getSystemProperties(new File(rootDir, "maven.properties")));
    }

    private static File rootDir(File wrapperJar) {
        return wrapperJar.getParentFile().getParentFile().getParentFile();
    }

    private static File wrapperProperties(File wrapperJar) {
        return new File(wrapperJar.getParent(), wrapperJar.getName().replaceFirst("\\.jar$", ".properties"));
    }

    private static File wrapperJar() {
        URI location;
        try {
            location = MavenWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        if (!location.getScheme().equals("file")) {
            throw new RuntimeException(String.format("Cannot determine classpath for wrapper Jar from codebase '%s'.", location));
        }
        return new File(location.getPath());
    }

    static String wrapperVersion() {
        try {
            InputStream resourceAsStream = MavenWrapperMain.class.getResourceAsStream("/META-INF/maven/io.takari/maven-wrapper/pom.properties");
            if (resourceAsStream == null) {
                throw new RuntimeException("No maven properties found.");
            }
            Properties mavenProperties = new Properties();
            try {
                mavenProperties.load(resourceAsStream);
                String version = mavenProperties.getProperty("version");
                if (version == null) {
                    throw new RuntimeException("No version number specified in build receipt resource.");
                }
                return version;
            } finally {
                resourceAsStream.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine wrapper version.", e);
        }
    }

    private static File mavenUserHome() {
        String mavenUserHome = System.getProperty(MAVEN_USER_HOME_PROPERTY_KEY);
        if (mavenUserHome != null) {
            return new File(mavenUserHome);
        } else if ((mavenUserHome = System.getenv(MAVEN_USER_HOME_ENV_KEY)) != null) {
            return new File(mavenUserHome);
        } else {
            return new File(DEFAULT_MAVEN_USER_HOME);
        }
    }
}

