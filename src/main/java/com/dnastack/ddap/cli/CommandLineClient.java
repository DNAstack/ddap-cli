package com.dnastack.ddap.cli;

import com.dnastack.ddap.cli.client.dam.DdapFrontendClient;
import com.dnastack.ddap.cli.client.dam.FeignClientBuilder;
import com.dnastack.ddap.cli.client.dam.model.*;
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
import lombok.Getter;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

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
                                                                       context.getCredentials(),
                                                                       jsonMapper,
                                                                       commandLine.hasOption("d"));

        final IOConsumer<ResourceTokens> outputAction;
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

            final DamInfo damInfo = context.getDamInfos().get(damId);
            if (damInfo == null) {
                System.err.printf("Invalid damId [%s]%n", damId);
                throw new SystemExit(1);
            }

            final Optional<View> maybeView = getView(ddapFrontendClient, damInfo.getUrl(), context.getRealm(), resourceId, viewId);
            if (maybeView.isEmpty()) {
                System.err.println("Mismatch in resource or view identifier. Make sure that given resource/view exits");
                throw new SystemExit(1);
            }

            final String roleId = getDefaultRole(maybeView.get());
            final ResourceTokens response = new GetAccessCommand(context, ddapFrontendClient, jsonMapper)
                    .getAccessToken(damInfo, resourceId, viewId, roleId);
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

    private static Optional<View> getView(DdapFrontendClient ddapFrontendClient, String damUrl, String realm, String resource, String view) {
        Map<String, Resource> resources = ddapFrontendClient.getResources(URI.create(damUrl), realm)
            .getResources();

        return resources.entrySet()
            .stream()
            .filter((entry) -> entry.getKey().equals(resource) && entry.getValue().getViews().containsKey(view))
            .map((entry) -> entry.getValue())
            .map(Resource::getViews)
            .map((views) -> views.get(view))
            .findFirst();
    }

    private static String getDefaultRole(View view) {
        return  view.getRoles().keySet()
            .iterator()
            .next();
    }

    private static void writeOutputToEnvFile(File outputFile, ResourceTokens response, PrintStream printStream) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            final StringBuilder exportStmtBuilder = new StringBuilder();
            final String access = response.getResources()
                                          .values()
                                          .stream()
                                          .map(ResourceTokens.Descriptor::getAccess)
                                          .findFirst()
                                          .orElseThrow(() -> new IllegalStateException("Token response must contain at least one resource"));
            final String accessToken = response.getAccess()
                                               .get(access)
                                               .getCredentials()
                                               .getAccessToken();
            exportStmtBuilder.append("export TOKEN=")
                             .append(accessToken)
                             .append(System.lineSeparator());

            final Optional<ResourceTokens.Interface> foundHttpGcsUri = response.getResources().keySet().stream()
                .map((resource) -> response.getResources().get(resource))
                .map(ResourceTokens.Descriptor::getInterfaces)
                .map((resourceInterface) -> {
                    return resourceInterface.entrySet().stream()
                        .filter((entry) -> entry.getKey().contains("http:gcp:gs"))
                        .map(Map.Entry::getValue)
                        .findFirst();
                })
                .collect(Collectors.toList())
                .get(0); // Assuming there is just one resource

            foundHttpGcsUri.ifPresent(interfaceObj -> exportStmtBuilder.append("export HTTP_BUCKET_URL=")
                                                              .append(interfaceObj.getItems().get(0).getUri())
                                                              .append(System.lineSeparator()));

            IOUtils.write(exportStmtBuilder.toString(), outputStream);
            printStream.printf("Output written to %s%n", outputFile.getPath());
            printStream.println("Use `source` to load into environment");
            printStream.println("Example:");
            printStream.println();
            printStream.printf("source %s%n", outputFile);
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
                                                                       context.getCredentials(),
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

        final String[] credentialsInput = parsedArgs.getOptionValues(CliOptions.USER_OPT);
        final Credentials credentials = (credentialsInput != null) ?
                new Credentials(credentialsInput[0], credentialsInput[1]) :
                null;

        final DdapFrontendClient ddapFrontendClient = buildFeignClient(ddapRootUrl, credentials, objectMapper, parsedArgs.hasOption("d"));

        final String realm = parsedArgs.getOptionValue(CliOptions.REALM_OPT, "dnastack");
        try {
            final Map<String, DamInfo> damInfos = ddapFrontendClient.getDamInfos();
            contextDAO.persist(new Context(ddapRootUrl, realm, damInfos, credentials));
            System.out.println("Login context saved");
        } catch (ContextDAO.PersistenceException e) {
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
                                                       Credentials credentials,
                                                       ObjectMapper objectMapper,
                                                       boolean debugLogging) {
        Optional<Credentials> maybeCredentials = Optional.ofNullable(credentials);
        Feign.Builder builder = FeignClientBuilder.getBuilder(objectMapper, debugLogging);

        return maybeCredentials
            .map((creds) -> {
                DdapHttpClient ddapClient = new DdapHttpClient();
                HttpCookie session = ddapClient.loginToDdap(ddapRootUrl, credentials.getUsername(), credentials.getPassword());
                return session.getValue();
            })
            .map((session) -> builder.requestInterceptor(template -> template.header("Cookie", "SESSION=" + session)))
            .orElse(builder)
            .target(DdapFrontendClient.class, ddapRootUrl);
    }

}
