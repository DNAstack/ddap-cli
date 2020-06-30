package com.dnastack.ddap.cli;

import static java.lang.String.format;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.FeignClientBuilder;
import com.dnastack.ddap.cli.client.dam.model.DamInfo;
import com.dnastack.ddap.cli.client.dam.model.ResourceResponse;
import com.dnastack.ddap.cli.client.dam.model.ResourceTokens;
import com.dnastack.ddap.cli.client.dam.model.View;
import com.dnastack.ddap.cli.client.ddap.DdapHttpClient;
import com.dnastack.ddap.cli.login.Context;
import com.dnastack.ddap.cli.login.ContextDAO;
import com.dnastack.ddap.cli.login.Credentials;
import com.dnastack.ddap.cli.resources.GetAccessCommand;
import com.dnastack.ddap.cli.resources.ListCommand;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import feign.Feign;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;

public class CommandLineClient {

    @Getter
    private static class SystemExit extends Exception {

        private final int status;

        SystemExit(int status, Throwable cause) {
            super(cause);
            this.status = status;
        }

        SystemExit(int status) {
            this.status = status;
        }
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (SystemExit systemExit) {
            final Option debugOption = CliOptions.debugOption();
            final boolean debugMode = Arrays.stream(args)
                .anyMatch(s -> s.equalsIgnoreCase("-" + debugOption.getOpt())
                    || s.equalsIgnoreCase("--" + debugOption.getLongOpt()));
            if (debugMode && systemExit.getCause() != null) {
                systemExit.getCause().printStackTrace(System.err);
            }
            System.exit(systemExit.getStatus());
        }
    }

    /**
     * Runs without calling {@link System#exit(int)}. Throws a {@link SystemExit} exception instead.
     *
     * @param args Command-line arguments.
     * @throws SystemExit The usual way this method returns, indicating that the script should exit with a given status.
     * May also contain a cause with debug information.
     */
    public static void run(String[] args) throws SystemExit {
        final Map<String, Options> optionsByCommand = CliOptions.getCommandOptions();

        if (args.length < 1) {
            executeHelpAndExit(optionsByCommand, 1);
        }

        final String command = args[0];
        final Options commandOptions = optionsByCommand.get(command);
        if (commandOptions == null) {
            executeHelpAndExit(optionsByCommand, 1);
        }

        final CommandLine parsedArgs = parseArgsOrExit(args, optionsByCommand, commandOptions);

        final ObjectMapper jsonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false);
        final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        final ContextDAO contextDAO = new ContextDAO(new File(System.getenv("HOME")), jsonMapper);

        switch (command) {
            case CliOptions.LOGIN_CMD:
                executeLoginAndExit(parsedArgs, jsonMapper, contextDAO);
            case CliOptions.LIST_CMD:
                executeListAndExit(parsedArgs, jsonMapper, yamlMapper, contextDAO);
            case CliOptions.GET_ACCESS_CMD:
                executeGetAccessAndExit(parsedArgs, jsonMapper, yamlMapper, contextDAO);
            case CliOptions.HELP_CMD:
                executeHelpAndExit(optionsByCommand, 0);
            default:
                executeHelpAndExit(optionsByCommand, 1);
        }
    }

    private static CommandLine parseArgsOrExit(String[] args,
                                               Map<String, Options> optionsByCommand,
                                               Options commandOptions) throws SystemExit {
        final CommandLine parsedArgs;
        try {
            final CommandLineParser parser = new DefaultParser();
            final String[] unparsedCommandArgs = Arrays.copyOfRange(args, 1, args.length);
            parsedArgs = parser.parse(commandOptions, unparsedCommandArgs);
        } catch (ParseException e) {
            executeHelpAndExitExceptionally(e, optionsByCommand, 1);
            // satisfy the compiler
            throw new AssertionError("Unreachable line.");
        }
        return parsedArgs;
    }

    @FunctionalInterface
    private interface IOConsumer<T> {

        void accept(T t) throws IOException;
    }

    private static void executeGetAccessAndExit(CommandLine commandLine,
        ObjectMapper jsonMapper,
        ObjectMapper yamlMapper,
        ContextDAO contextDAO) throws SystemExit {
        final Context context = loadContextOrExit(contextDAO);
        final DdapFrontendClient ddapFrontendClient = buildFeignClient(context.getUrl(),
            context,
            jsonMapper,
            commandLine.hasOption("d"));
        persistContextIfRequired(contextDAO, context);
        final IOConsumer<ResourceTokens> outputAction;
        if (commandLine.hasOption(CliOptions.FILE_OPT)) {
            final File outputFile = setupEnvFileOrExit(commandLine);
            outputAction = response -> writeOutputToEnvFile(outputFile, response, System.out);
        } else {
            outputAction = response -> yamlMapper.writer().writeValue(System.out, response);
        }
        try {
            final String interfaceId = commandLine.getOptionValue(CliOptions.INTERFACE_ID_OPT);
            final ResourceTokens response = new GetAccessCommand(context, ddapFrontendClient, jsonMapper)
                .getAccessToken(interfaceId);
            System.out.println("Access token acquired");
            outputAction.accept(response);
        }
        catch (GetAccessCommand.GetAccessException e) {
            System.out.println(e.getMessage());
            throw new SystemExit(1, e);
        } catch (IOException e) {
            System.err.println("Unable to serialize response.");
            throw new SystemExit(1, e);
        }

        throw new SystemExit(0);
    }

    private static void writeOutputToEnvFile(File outputFile, ResourceTokens response, PrintStream printStream) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            final StringBuilder exportStmtBuilder = new StringBuilder();
            final String accessToken = response.getAccessToken();
            if(accessToken == null) {
                throw new IllegalStateException("Token response must contain an access token");
            }
            exportStmtBuilder.append("export TOKEN=")
                .append(accessToken)
                .append(System.lineSeparator());

            IOUtils.write(exportStmtBuilder.toString(), outputStream);
            printStream.printf("Output written to %s%n", outputFile.getPath());
            printStream.println("Use `source` to load into environment");
            printStream.println("Example:");
            printStream.println();
            printStream.printf("source %s%n", outputFile);
        }
    }

    private static File setupEnvFileOrExit(CommandLine commandLine) throws SystemExit {
        final File outputFile = new File(commandLine.getOptionValue(CliOptions.FILE_OPT));
        try {
            outputFile.createNewFile();
            if (!outputFile.canWrite()) {
                throw new IOException("Can't write to given file path");
            }
        } catch (IOException e) {
            System.err.println(format("Problem with output file [%s]: %s", outputFile.getPath(), e.getMessage()));
            throw new SystemExit(1, e);
        }
        return outputFile;
    }

    private static void executeListAndExit(CommandLine commandLine,
        ObjectMapper jsonMapper,
        ObjectMapper yamlMapper,
        ContextDAO contextDAO) throws SystemExit {
        final Context context = loadContextOrExit(contextDAO);
        final DdapFrontendClient ddapFrontendClient = buildFeignClient(context.getUrl(),
            context,
            jsonMapper,
            commandLine.hasOption("d"));
        persistContextIfRequired(contextDAO, context);
        try {
            final ResourceResponse resourceResponseByDamId = new ListCommand(context,
                ddapFrontendClient,
                jsonMapper).listResources();
            yamlMapper.writer().writeValue(System.out, resourceResponseByDamId);
        } catch (ListCommand.ListException e) {
            System.err.println(e.getMessage());
            throw new SystemExit(1, e);
        } catch (IOException e) {
            System.err.println("Unable to serialize response to standard out.");
            throw new SystemExit(1, e);
        }

        throw new SystemExit(0);
    }

    private static Context loadContextOrExit(ContextDAO contextDAO) throws SystemExit {
        try {
            return contextDAO.load();
        } catch (ContextDAO.PersistenceException e) {
            System.err.println(e.getMessage());
            System.err.println("Try running the 'login' command.");
            throw new SystemExit(1, e);
        }
    }

    private static void executeLoginAndExit(CommandLine parsedArgs, ObjectMapper objectMapper, ContextDAO contextDAO) throws SystemExit {
        final String ddapRootUrl = parsedArgs.getOptionValue(CliOptions.LOCATION_OPT);

        final String[] credentialsInput = parsedArgs.getOptionValues(CliOptions.USER_OPT);
        final Credentials credentials = (credentialsInput != null) ?
            new Credentials(credentialsInput[0], credentialsInput[1], null, null) :
            null;

        Context context = new Context();
        context.setCredentials(credentials);

        final DdapFrontendClient ddapFrontendClient = buildFeignClient(ddapRootUrl, context, objectMapper, parsedArgs
            .hasOption("d"));

        final String realm = parsedArgs.getOptionValue(CliOptions.REALM_OPT, "dnastack");
        final Map<String, DamInfo> damInfos = ddapFrontendClient.getDamInfos();
        context.setRealm(realm);
        context.setDamInfos(damInfos);
        context.setUrl(ddapRootUrl);
        context.setChanged(true);
        persistContextIfRequired(contextDAO, context);
        throw new SystemExit(0);
    }

    private static void executeHelpAndExit(Map<String, Options> optionsByCommand, int helpExitStatus) throws SystemExit {
        CliOptions.printHelpMessage(optionsByCommand);
        throw new SystemExit(helpExitStatus);
    }

    private static void executeHelpAndExitExceptionally(Throwable throwable, Map<String, Options> optionsByCommand, int helpExitStatus) throws SystemExit {
        if (throwable != null && throwable.getMessage() != null) {
            System.err.println(throwable.getMessage());
        }
        CliOptions.printHelpMessage(optionsByCommand);
        throw new SystemExit(helpExitStatus, throwable);
    }


    private static void persistContextIfRequired(ContextDAO contextDAO, Context context) throws SystemExit {
        if (context.isChanged()) {
            persistContext(contextDAO, context);
        }
    }

    private static void persistContext(ContextDAO contextDAO, Context context) throws SystemExit {
        try {
            contextDAO.persist(context);
            System.out.println("Login context saved");
        } catch (ContextDAO.PersistenceException e) {
            System.err.println(e.getMessage());
            throw new SystemExit(1, e);
        }
    }

    private static DdapFrontendClient buildFeignClient(String ddapRootUrl,
        Context context,
        ObjectMapper objectMapper,
        boolean debugLogging) {
        Feign.Builder builder = FeignClientBuilder.getBuilder(objectMapper, debugLogging);

        Credentials credentials = context.getCredentials() != null ? context.getCredentials() : new Credentials();

        DdapHttpClient ddapHttpClient = new DdapHttpClient();

        ddapHttpClient.loginToDdap(ddapRootUrl, context.getCredentials())
            .forEach(cookie -> {
                if (cookie.getName().equals("SESSION") && !cookie.getValue().equals(credentials.getSessionId())) {
                    credentials.setSessionId(cookie.getValue());
                    context.setChanged(true);
                } else if (cookie.getName().equals("SESSION_DECRYPTION_KEY") && !cookie.getValue()
                    .equals(credentials.getSessionDecryptionKey())) {
                    credentials.setSessionDecryptionKey(cookie.getValue());
                    context.setChanged(true);
                }
            });
        context.setCredentials(credentials);

        String sessionCookieString = String
            .format("SESSION=%s;SESSION_DECRYPTION_KEY=%s", context.getCredentials().getSessionId(), context
                .getCredentials().getSessionDecryptionKey());
        return builder.requestInterceptor(template -> template.header("Cookie", sessionCookieString))
            .target(DdapFrontendClient.class, ddapRootUrl);
    }

}
