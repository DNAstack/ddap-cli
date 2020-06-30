package com.dnastack.ddap.cli;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;

import java.util.HashMap;
import java.util.Map;

class CliOptions {
    private static final String PRINT_OPT = "p";
    static final String FILE_OPT = "f";
    static final String INTERFACE_ID_OPT = "i";
    static final String RESOURCE_OPT = "r";
    static final String VIEW_OPT = "v";
    static final String LOCATION_OPT = "l";
    static final String USER_OPT = "u";
    static final String REALM_OPT = "r";

    static final String LOGIN_CMD = "login";
    static final String LIST_CMD = "list";
    static final String GET_ACCESS_CMD = "get-access";
    static final String HELP_CMD = "help";

    static void addGlobalOptions(Options options) {
        options.addOption(debugOption());
    }

    static Option debugOption() {
        return Option.builder("d")
                     .longOpt("debug")
                     .desc("Enable stack traces on error.")
                     .required(false)
                     .hasArg(false)
                     .type(Boolean.class)
                     .build();
    }

    static Options helpOptions() {
        return new Options();
    }

    static Options listOptions() {
        return new Options();
    }

    static Options getAccessOptions() {
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
                .addOption(Option.builder(INTERFACE_ID_OPT)
                                 .longOpt("interfaceId")
                                 .required()
                                 .desc("An ID of an Interface")
                                 .hasArg()
                                 .build())
                .addOptionGroup(outputGroup);
    }

    static Options loginOptions() {
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
                                 .desc("Username and password.")
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

    public static Map<String, Options> getCommandOptions() {
        final Map<String, Options> optionsByCommand = new HashMap<>();
        optionsByCommand.put(LOGIN_CMD, loginOptions());
        optionsByCommand.put(LIST_CMD, listOptions());
        optionsByCommand.put(GET_ACCESS_CMD, getAccessOptions());
        optionsByCommand.put(HELP_CMD, helpOptions());

        optionsByCommand.forEach((k, v) -> addGlobalOptions(v));
        return optionsByCommand;
    }

    static void printHelpMessage(Map<String, Options> optionsByCommand) {
        final HelpFormatter formatter = new HelpFormatter();
        optionsByCommand.forEach(formatter::printHelp);
    }
}
