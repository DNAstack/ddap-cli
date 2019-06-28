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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import feign.Feign;
import feign.Logger;
import feign.jackson.JacksonDecoder;
import lombok.Getter;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import static java.lang.String.format;

public class CommandLineClient {

    private static final String PRINT_OPT = "p";
    private static final String FILE_OPT = "f";
    private static final String RESOURCE_OPT = "r";
    private static final String VIEW_OPT = "v";
    private static final String TTL_OPT = "t";
    private static final String LOCATION_OPT = "l";
    private static final String USER_OPT = "u";
    private static final String REALM_OPT = "r";

    private static final String LOGIN_CMD = "login";
    private static final String LIST_CMD = "list";
    private static final String GET_ACCESS_CMD = "get-access";
    private static final String HELP_CMD = "help";

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
            final Option debugOption = debugOption();
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
        final Map<String, Options> optionsByCommand = new HashMap<>();
        optionsByCommand.put(LOGIN_CMD, loginOptions());
        optionsByCommand.put(LIST_CMD, listOptions());
        optionsByCommand.put(GET_ACCESS_CMD, getAccessOptions());
        optionsByCommand.put(HELP_CMD, helpOptions());

        optionsByCommand.forEach((k, v) -> addGlobalOptions(v));

        if (args.length < 1) {
            executeHelpAndExit(optionsByCommand, 1);
        }

        final String command = args[0];
        final Options commandOptions = optionsByCommand.get(command);
        if (commandOptions == null) {
            executeHelpAndExit(optionsByCommand, 1);
        }

        final CommandLineParser parser = new DefaultParser();
        final String[] unparsedCommandArgs = Arrays.copyOfRange(args, 1, args.length);
        final CommandLine parsedArgs;
        try {
            parsedArgs = parser.parse(commandOptions, unparsedCommandArgs);
        } catch (ParseException e) {
            executeHelpAndExit(optionsByCommand, 1);
            // satisfy the compiler
            throw new AssertionError("Unreachable line.");
        }

        final ObjectMapper jsonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                                                     false);
        final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        final ContextDAO contextDAO = new ContextDAO(new File(System.getenv("HOME")), jsonMapper);

        switch (command) {
            case LOGIN_CMD:
                executeLoginAndExit(parsedArgs, jsonMapper, contextDAO);
            case LIST_CMD:
                executeListAndExit(parsedArgs, jsonMapper, yamlMapper, contextDAO);
            case GET_ACCESS_CMD:
                executeGetAccessAndExit(parsedArgs, jsonMapper, yamlMapper, contextDAO);
            case HELP_CMD:
                executeHelpAndExit(optionsByCommand, 0);
            default:
                executeHelpAndExit(optionsByCommand, 1);
        }
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
        if (commandLine.hasOption(FILE_OPT)) {
            final File outputFile = new File(commandLine.getOptionValue(FILE_OPT));
            try {
                outputFile.createNewFile();
                if (!outputFile.canWrite()) {
                    throw new IOException("Can't write to given file path");
                }
            } catch (IOException e) {
                System.err.println(format("Problem with output file [%s]: %s", outputFile.getPath(), e.getMessage()));
                throw new SystemExit(1, e);
            }
            outputAction = response -> {
                try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                    final StringBuilder exportStmtBuilder = new StringBuilder();
                    exportStmtBuilder.append("TOKEN=")
                                     .append(response.getToken())
                                     .append('\n');
                    final Interfaces interfaces = response.getView().getInterfaces();

                    final Optional<String> foundHttpGcsUri = Optional.ofNullable(interfaces.getHttpGcs())
                                                                     .map(GcsInterface::getUri)
                                                                     .stream()
                                                                     .flatMap(Collection::stream)
                                                                     .findFirst();
                    foundHttpGcsUri.ifPresent(uri -> exportStmtBuilder.append("HTTP_BUCKET_URL=")
                                                                      .append(uri)
                                                                      .append('\n'));

                    IOUtils.write(exportStmtBuilder.toString(), outputStream);
                    System.out.printf("Output written to %s\n", outputFile.getPath());
                    System.out.println("Use `source` to load into environment");
                    System.out.println("Example:");
                    System.out.println();
                    System.out.printf("source %s\n", outputFile);
                    if (foundHttpGcsUri.isPresent()) {
                        System.out.println("curl ${HTTP_BUCKET_URL}/o?access_token=${TOKEN}");
                    }
                }
            };
        } else {
            outputAction = response -> yamlMapper.writer().writeValue(System.out, response);
        }
        try {
            final String resourceId = commandLine.getOptionValue(RESOURCE_OPT);
            final String viewId = commandLine.getOptionValue(VIEW_OPT);
            final String ttl = commandLine.getOptionValue(TTL_OPT, "1h");

            final ViewAccessTokenResponse response = new GetAccessCommand(context, ddapFrontendClient, jsonMapper)
                    .getAccessToken(resourceId,
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
            final ResourceResponse resourceResponse = new ListCommand(context,
                                                                      ddapFrontendClient,
                                                                      jsonMapper).listResources();
            yamlMapper.writer().writeValue(System.out, resourceResponse);
        } catch (ListCommand.ListException e) {
            System.err.println(e.getMessage());
            throw new SystemExit(1, e);
        } catch (IOException e) {
            System.err.println("Unable to serialize response to standard out.");
            throw new SystemExit(1, e);
        }

        throw new SystemExit(0);
    }

    private static void addGlobalOptions(Options options) {
        options.addOption(debugOption());
    }

    private static Option debugOption() {
        return Option.builder("d")
                     .longOpt("debug")
                     .desc("Enable stack traces on error.")
                     .required(false)
                     .hasArg(false)
                     .type(Boolean.class)
                     .build();
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
        final String ddapRootUrl = parsedArgs.getOptionValue(LOCATION_OPT);

        final String[] basicAuth = parsedArgs.getOptionValues(USER_OPT);
        final BasicCredentials basicCredentials = (basicAuth != null) ?
                new BasicCredentials(basicAuth[0], basicAuth[1]) :
                null;

        final DdapFrontendClient ddapFrontendClient = buildFeignClient(ddapRootUrl,
                                                                       basicCredentials,
                                                                       objectMapper,
                                                                       parsedArgs.hasOption("d"));

        final String realm = parsedArgs.getOptionValue(REALM_OPT, "dnastack");
        final LoginCommand loginCommand = new LoginCommand(objectMapper, ddapFrontendClient, realm);
        try {
            final LoginTokenResponse loginTokenResponse = loginCommand.login();
            contextDAO.persist(new Context(ddapRootUrl, loginCommand.getRealm(), loginTokenResponse, basicCredentials));
            System.out.println("Login context saved");
        } catch (LoginCommand.LoginException | ContextDAO.PersistenceException e) {
            System.err.println(e.getMessage());
            throw new SystemExit(1, e);
        }
        throw new SystemExit(0);
    }

    private static void executeHelpAndExit(Map<String, Options> optionsByCommand, int helpExitStatus) throws SystemExit {
        printHelpMessage(optionsByCommand);
        throw new SystemExit(helpExitStatus);
    }

    private static void printHelpMessage(Map<String, Options> optionsByCommand) {
        final HelpFormatter formatter = new HelpFormatter();
        optionsByCommand.forEach(formatter::printHelp);
    }

    private static Options helpOptions() {
        return new Options();
    }

    private static Options listOptions() {
        return new Options();
    }

    private static Options getAccessOptions() {
        final OptionGroup outputGroup = new OptionGroup()
                .addOption(Option.builder(PRINT_OPT)
                                 .longOpt("print")
                                 .desc("Print tokens to stdout")
                                 .hasArg(false)
                                 .build())
                .addOption(Option.builder(FILE_OPT)
                                 .longOpt("env-file")
                                 .desc("Write tokens to given file path with shell export statements.")
                                 .hasArg(true)
                                 .type(String.class)
                                 .build());

        return new Options()
                .addOption(Option.builder(RESOURCE_OPT)
                                 .longOpt("resource")
                                 .required()
                                 .desc("A DAM resource to request access")
                                 .hasArg()
                                 .build())
                .addOption(Option.builder(VIEW_OPT)
                                 .longOpt("view")
                                 .required()
                                 .desc("A DAM view to request access")
                                 .hasArg()
                                 .build())
                .addOption(Option.builder(TTL_OPT)
                                 .longOpt("ttl")
                                 .required(false)
                                 .desc("Access token TTL")
                                 .hasArg()
                                 .build())
                .addOptionGroup(outputGroup);
    }

    private static Options loginOptions() {
        return new Options()
                .addOption(Option.builder(LOCATION_OPT)
                                 .longOpt("location")
                                 .desc("DDAP URL")
                                 .required()
                                 .hasArg()
                                 .type(String.class)
                                 .build())
                .addOption(Option.builder(USER_OPT)
                                 .longOpt("user")
                                 .desc("Basic auth username and password.")
                                 .required(false)
                                 .hasArgs()
                                 .numberOfArgs(2)
                                 .type(String.class)
                                 .build())
                .addOption(Option.builder(REALM_OPT)
                                 .longOpt("realm")
                                 .desc("DDAP realm.")
                                 .required(false)
                                 .hasArg()
                                 .type(String.class)
                                 .build());
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
                                           .decoder(new JacksonDecoder(objectMapper))
                                           .logLevel(debugLogging ? Logger.Level.FULL : Logger.Level.NONE)
                                           .logger(new Logger() {
                                               @Override
                                               protected void log(String configKey, String format, Object... args) {
                                                   System.out.printf(configKey + " " + format + "\n", args);
                                               }
                                           });

        return encodedCredentials
                .map(ec -> builder.requestInterceptor(template -> template.header("Authorization",
                                                                                  "Basic " + ec)))
                .orElse(builder)
                .target(DdapFrontendClient.class, ddapRootUrl);
    }

}
