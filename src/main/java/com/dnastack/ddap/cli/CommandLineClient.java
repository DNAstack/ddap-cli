package com.dnastack.ddap.cli;

import com.dnastack.ddap.cli.client.dam.*;
import com.dnastack.ddap.cli.login.BasicCredentials;
import com.dnastack.ddap.cli.login.Context;
import com.dnastack.ddap.cli.login.ContextDAO;
import com.dnastack.ddap.cli.login.LoginCommand;
import com.dnastack.ddap.cli.resources.GetAccessCommand;
import com.dnastack.ddap.cli.resources.ListCommand;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import feign.*;
import feign.jackson.JacksonDecoder;
import feign.okhttp.OkHttpClient;
import lombok.Getter;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

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
     * @throws SystemExit The usual way this method returns,
    indicating that the script should exit with a given status. May also contain a cause with debug information.
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

    private static CommandLine parseArgsOrExit(String[] args, Map<String, Options> optionsByCommand, Options commandOptions) throws SystemExit {
        final CommandLine parsedArgs;
        try {
            final CommandLineParser parser = new DefaultParser();
            final String[] unparsedCommandArgs = Arrays.copyOfRange(args, 1, args.length);
            parsedArgs = parser.parse(commandOptions, unparsedCommandArgs);
        } catch (ParseException e) {
            executeHelpAndExit(optionsByCommand, 1);
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
                                                                       context.getBasicCredentials(),
                                                                       jsonMapper,
                                                                       commandLine.hasOption("d"));

        final IOConsumer<ViewAccessTokenResponse> outputAction;
        if (commandLine.hasOption(CliOptions.FILE_OPT)) {
            final File outputFile = setupEnvFileOrExit(commandLine);
            outputAction = response -> writeOutputToEnvFile(outputFile, response, System.out);
        } else {
            outputAction = response -> yamlMapper.writer().writeValue(System.out, response);
        }
        try {
            final String damId = commandLine.getOptionValue(CliOptions.DAM_ID_OPT);
            final String resourceId = commandLine.getOptionValue(CliOptions.RESOURCE_OPT);
            final String viewId = commandLine.getOptionValue(CliOptions.VIEW_OPT);
            final String ttl = commandLine.getOptionValue(CliOptions.TTL_OPT, "1h");

            final DamInfo damInfo = context.getDamInfos().get(damId);
            if (damInfo == null) {
                System.err.printf("Invalid damId [%s]\n", damId);
                throw new SystemExit(1);
            }

            final ViewAccessTokenResponse response = new GetAccessCommand(context, ddapFrontendClient, jsonMapper)
                    .getAccessToken(damInfo,
                                    resourceId,
                                    viewId,
                                    ttl);
            System.out.println("Access token acquired");
            outputAction.accept(response);
        } catch (GetAccessCommand.GetAccessException e) {
            System.out.println(e.getMessage());
            throw new SystemExit(1, e);
        } catch (IOException e) {
            System.err.println("Unable to serialize response.");
            throw new SystemExit(1, e);
        }

        throw new SystemExit(0);
    }

    private static void writeOutputToEnvFile(File outputFile, ViewAccessTokenResponse response, PrintStream printStream) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            final StringBuilder exportStmtBuilder = new StringBuilder();
            exportStmtBuilder.append("export TOKEN=")
                             .append(response.getToken())
                             .append('\n');
            final Interfaces interfaces = response.getView().getInterfaces();

            final Optional<String> foundHttpGcsUri = Optional.ofNullable(interfaces.getHttpGcs())
                                                             .map(GcsInterface::getUri)
                                                             .stream()
                                                             .flatMap(Collection::stream)
                                                             .findFirst();
            foundHttpGcsUri.ifPresent(uri -> exportStmtBuilder.append("export HTTP_BUCKET_URL=")
                                                              .append(uri)
                                                              .append('\n'));

            IOUtils.write(exportStmtBuilder.toString(), outputStream);
            printStream.printf("Output written to %s\n", outputFile.getPath());
            printStream.println("Use `source` to load into environment");
            printStream.println("Example:");
            printStream.println();
            printStream.printf("source %s\n", outputFile);
            if (foundHttpGcsUri.isPresent()) {
                printStream.println("curl ${HTTP_BUCKET_URL}/o?access_token=${TOKEN}");
            }
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
                                                                       context.getBasicCredentials(),
                                                                       jsonMapper,
                                                                       commandLine.hasOption("d"));
        try {
            final Map<String, ResourceResponse> resourceResponseByDamId = new ListCommand(context,
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

        final String[] basicAuth = parsedArgs.getOptionValues(CliOptions.USER_OPT);
        final BasicCredentials basicCredentials = (basicAuth != null) ?
                new BasicCredentials(basicAuth[0], basicAuth[1]) :
                null;

        final DdapFrontendClient ddapFrontendClient = buildFeignClient(ddapRootUrl,
                                                                       basicCredentials,
                                                                       objectMapper,
                                                                       parsedArgs.hasOption("d"));

        final String realm = parsedArgs.getOptionValue(CliOptions.REALM_OPT, "dnastack");
        final LoginCommand loginCommand = new LoginCommand(objectMapper, ddapFrontendClient, realm);
        try {
            final LoginTokenResponse loginTokenResponse = loginCommand.login();
            final Map<String, DamInfo> damInfos = ddapFrontendClient.getDamInfos();
            contextDAO.persist(new Context(ddapRootUrl, loginCommand.getRealm(), damInfos, loginTokenResponse, basicCredentials));
            System.out.println("Login context saved");
        } catch (LoginCommand.LoginException | ContextDAO.PersistenceException e) {
            System.err.println(e.getMessage());
            throw new SystemExit(1, e);
        }
        throw new SystemExit(0);
    }

    private static void executeHelpAndExit(Map<String, Options> optionsByCommand, int helpExitStatus) throws SystemExit {
        CliOptions.printHelpMessage(optionsByCommand);
        throw new SystemExit(helpExitStatus);
    }

    private static DdapFrontendClient buildFeignClient(String ddapRootUrl,
                                                       BasicCredentials basicCredentials,
                                                       ObjectMapper objectMapper,
                                                       boolean debugLogging) {
        final Optional<String> encodedCredentials = Optional.ofNullable(basicCredentials)
                                                            .map(bc -> Base64.getEncoder()
                                                                             .encodeToString((bc.getUsername() + ":" + bc
                                                                                     .getPassword()).getBytes()));
        final Feign.Builder builder = Feign.builder()
                                           .client(new OkHttpClient())
                                           .decoder(new JacksonDecoder(objectMapper))
                                           .logLevel(debugLogging ? Logger.Level.FULL : Logger.Level.NONE)
                                           .logger(new Logger() {
                                               @Override
                                               protected void log(String configKey, String format, Object... args) {
                                                   System.out.printf(configKey + " " + format + "\n", args);
                                               }
                                           });

        return encodedCredentials
                .map(ec -> builder.requestInterceptor(template -> template.header("Authorization", "Basic " + ec)))
                .orElse(builder)
                .target(DdapFrontendClient.class, ddapRootUrl);
    }

}
