package com.dnastack.ddap.cli.login;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import static java.lang.String.format;

@AllArgsConstructor
public class ContextDAO {
    private final File homeDirectory;
    private final ObjectMapper objectMapper;

    public static class PersistenceException extends Exception {
        PersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public void persist(Context context) throws PersistenceException {
        final File contextFile = contextFile();
        if (!contextFile.exists()) {
            try {
                contextFile.createNewFile();
            } catch (IOException e) {
                throw new PersistenceException(format("Unable to create persistence file [%s]", contextFile), e);
            }
        }
        try {
            objectMapper.writer().writeValue(contextFile, context);
        } catch (IOException e) {
            throw new PersistenceException(format("Unable to serialize context [%s]", context), e);
        }
    }

    public File contextFile() {
        return new File(homeDirectory, ".ddap-cli");
    }

    public Context load() throws PersistenceException {
        final File contextFile = contextFile();
        try (final FileReader reader = new FileReader(contextFile)) {
            return objectMapper.readValue(reader, Context.class);
        } catch (JsonParseException | JsonMappingException e) {
            throw new PersistenceException(format("Could not parse contents of context file [%s]", contextFile), e);
        } catch (FileNotFoundException e) {
            throw new PersistenceException(format("Context file does not exist [%s]", contextFile), e);
        } catch (IOException e) {
            throw new PersistenceException(format("Unable to read context file [%s]", contextFile), e);
        }
    }
}
