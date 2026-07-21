package ru.senioravanti.imgconv.utils;


import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toCollection;

import ru.senioravanti.imgconv.model.ConfigurationProperties;

public class ConfigurationLoader {
    private static final Pattern UNRESOLVED_ENV_PATTERN = Pattern.compile("\\$\\{([A-Z0-9_]+)?}?");

    private static final YAMLMapper YAML = YAMLMapper.builder(new YAMLFactory())
        .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
        .build();
    private static final StringSubstitutor STRING_SUBSTITUTOR = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());

    private static Set<String> findMissingEnvs(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream()
                .flatMap(it -> findMissingEnvs(it.getValue()).stream())
                .collect(toCollection(LinkedHashSet::new));
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                .flatMap(it -> findMissingEnvs(it).stream())
                .collect(toCollection(LinkedHashSet::new));
        }
        if (node.isString()) {
            var matcher = UNRESOLVED_ENV_PATTERN.matcher(node.stringValue());
            if (matcher.find()) {
                return Set.of(matcher.group(1));
            }
        }
        return Set.of();
    }

    private static <T> T loadConfiguration(InputStream inputStream, Class<T> clazz) throws IOException {
        var contents = STRING_SUBSTITUTOR.replace(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        var tree = YAML.readTree(contents);
        var missingEnvs = findMissingEnvs(tree);
        if (!missingEnvs.isEmpty()) {
            throw new RuntimeException("missing environment variables: " + missingEnvs);
        }
        return YAML.treeToValue(tree, clazz);
    }

    public static ConfigurationProperties loadConfig(String name) {
        try (var resource = ConfigurationLoader.class.getClassLoader().getResourceAsStream(name)) {
            if (resource == null) {
                throw new RuntimeException("resource `%s` could not be found".formatted(name));
            }
            return loadConfiguration(resource, ConfigurationProperties.class);
        } catch (IOException ex) {
            throw new RuntimeException("failed to read `%s`".formatted(name), ex);
        } catch (JacksonException ex) {
            throw new RuntimeException("failed to parse `%s`".formatted(name), ex);
        }
    }
}
