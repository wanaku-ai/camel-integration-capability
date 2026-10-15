package ai.wanaku.capability.camel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelToolMainTest {
    private static final Path ROUTES = Path.of("src", "test", "resources", "mcp-tool-routes.camel.yaml");

    private static final List<String> VALID_REGISTRATION = List.of(
            "--routes-ref",
            "file:///routes.camel.yaml",
            "--register",
            "--wanaku-url",
            "http://wanaku:8080",
            "--advertised-address",
            "http://echo-tool:9090/mcp",
            "--name",
            "echo-tool");

    /** Runs the valid registration arguments with one option set to another value. */
    private static String usageError(String option, String value) {
        List<String> args = new ArrayList<>(VALID_REGISTRATION);
        int index = args.indexOf(option);
        if (index < 0) {
            args.addAll(List.of(option, value));
        } else {
            args.set(index + 1, value);
        }
        return usageErrorFor(args);
    }

    private static List<String> without(String option) {
        List<String> args = new ArrayList<>(VALID_REGISTRATION);
        int index = args.indexOf(option);
        args.remove(index + 1);
        args.remove(index);
        return args;
    }

    private static String usageErrorFor(List<String> args) {
        StringWriter err = new StringWriter();
        CommandLine commandLine = new CommandLine(new CamelToolMain());
        commandLine.setErr(new PrintWriter(err));

        int exitCode = commandLine.execute(args.toArray(String[]::new));

        assertEquals(CommandLine.ExitCode.USAGE, exitCode, err.toString());
        return err.toString();
    }

    @Test
    void registerRequiresWanakuUrl() {
        assertTrue(usageErrorFor(without("--wanaku-url")).contains("--wanaku-url is required"));
    }

    @Test
    void registerRequiresAdvertisedAddress() {
        assertTrue(usageErrorFor(without("--advertised-address")).contains("--advertised-address is required"));
    }

    @Test
    void registerRejectsInvalidForwardName() {
        assertTrue(usageError("--name", "bad/name").contains("--name must contain only"));
    }

    @Test
    void registerRejectsNonHttpAddress() {
        assertTrue(usageError("--advertised-address", "echo-tool:9090").contains("--advertised-address must be"));
    }

    @Test
    void registerRequiresMcpServer() {
        assertTrue(usageError("--mcp-port", "0").contains("requires the MCP server"));
    }

    @Test
    void registerRequiresTokenVariableToBeSet() {
        assertTrue(usageError("--wanaku-token-env", "CIC_TEST_TOKEN_THAT_IS_NOT_SET")
                .contains("--wanaku-token-env is not set"));
    }

    @Test
    void registrationIsDisabledByDefault() throws Exception {
        CamelToolMain main = new CamelToolMain();
        new CommandLine(main).parseArgs("--routes-ref", "file:///routes.camel.yaml", "--name", "any/name");

        main.validateRegistrationOptions();

        assertNull(main.createRegistration(ROUTES));
    }

    @Test
    void registerAcceptsExplicitFalseFromContainerEnvironment() throws Exception {
        CamelToolMain main = new CamelToolMain();
        new CommandLine(main).parseArgs("--routes-ref", "file:///routes.camel.yaml", "--register=false");

        main.validateRegistrationOptions();

        assertNull(main.createRegistration(ROUTES));
    }

    @Test
    void validRegistrationOptionsCreateRegistration() throws Exception {
        CamelToolMain main = new CamelToolMain();
        new CommandLine(main).parseArgs(VALID_REGISTRATION.toArray(String[]::new));

        main.validateRegistrationOptions();

        assertNotNull(main.createRegistration(ROUTES));
    }
}
