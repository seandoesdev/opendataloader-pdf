package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads journal-specific paper templates from classpath or filesystem and provides
 * lookup by DOI prefix, ISSN, or journal name pattern.
 */
public class TemplateRegistry {

    private static final Logger logger = Logger.getLogger(TemplateRegistry.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLASSPATH_PREFIX = "/paper-templates/";

    private final List<JournalEntry> entries;
    private final Map<String, JsonNode> templateCache;
    private final JsonNode defaultTemplate;

    private static class JournalEntry {
        final String journalId;
        final List<String> doiPrefixes;
        final List<String> issn;
        final List<String> namePatterns;

        JournalEntry(String journalId, List<String> doiPrefixes, List<String> issn, List<String> namePatterns) {
            this.journalId = journalId;
            this.doiPrefixes = doiPrefixes;
            this.issn = issn;
            this.namePatterns = namePatterns;
        }
    }

    private TemplateRegistry(List<JournalEntry> entries, Map<String, JsonNode> templateCache, JsonNode defaultTemplate) {
        this.entries = entries;
        this.templateCache = templateCache;
        this.defaultTemplate = defaultTemplate;
    }

    /**
     * Loads the template registry from the classpath ({@code /paper-templates/}).
     */
    public static TemplateRegistry loadDefault() {
        try (InputStream registryStream = TemplateRegistry.class.getResourceAsStream(CLASSPATH_PREFIX + "_registry.json")) {
            if (registryStream == null) {
                throw new IOException("_registry.json not found on classpath");
            }
            JsonNode registryNode = MAPPER.readTree(registryStream);
            List<JournalEntry> entries = parseEntries(registryNode);
            Map<String, JsonNode> cache = new HashMap<>();

            for (JournalEntry entry : entries) {
                String resourcePath = CLASSPATH_PREFIX + entry.journalId + ".json";
                try (InputStream ts = TemplateRegistry.class.getResourceAsStream(resourcePath)) {
                    if (ts != null) {
                        cache.put(entry.journalId, MAPPER.readTree(ts));
                    } else {
                        logger.log(Level.WARNING, "Template not found on classpath: {0}", resourcePath);
                    }
                }
            }

            JsonNode defaultTpl = loadDefaultTemplate(cache);
            return new TemplateRegistry(entries, cache, defaultTpl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load template registry from classpath", e);
        }
    }

    /**
     * Loads the template registry from a filesystem directory.
     */
    public static TemplateRegistry loadFromDir(String dir) {
        Path dirPath = Paths.get(dir);
        try (InputStream registryStream = Files.newInputStream(dirPath.resolve("_registry.json"))) {
            JsonNode registryNode = MAPPER.readTree(registryStream);
            List<JournalEntry> entries = parseEntries(registryNode);
            Map<String, JsonNode> cache = new HashMap<>();

            for (JournalEntry entry : entries) {
                Path templatePath = dirPath.resolve(entry.journalId + ".json");
                if (Files.exists(templatePath)) {
                    try (InputStream ts = Files.newInputStream(templatePath)) {
                        cache.put(entry.journalId, MAPPER.readTree(ts));
                    }
                } else {
                    logger.log(Level.WARNING, "Template not found: {0}", templatePath);
                }
            }

            JsonNode defaultTpl = loadDefaultTemplateFromDir(dirPath, cache);
            return new TemplateRegistry(entries, cache, defaultTpl);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load template registry from directory: " + dir, e);
        }
    }

    /**
     * Finds a template by matching the DOI against registered DOI prefixes.
     * Returns the default template if no match is found.
     */
    public JsonNode findTemplate(String doi) {
        if (doi != null) {
            for (JournalEntry entry : entries) {
                for (String prefix : entry.doiPrefixes) {
                    if (doi.startsWith(prefix)) {
                        JsonNode tpl = templateCache.get(entry.journalId);
                        if (tpl != null) {
                            return tpl;
                        }
                    }
                }
            }
        }
        return defaultTemplate;
    }

    /**
     * Finds a template by searching for ISSN or journal name patterns within the given text.
     * Returns the default template if no match is found.
     */
    public JsonNode findTemplateByText(String text) {
        if (text != null) {
            for (JournalEntry entry : entries) {
                for (String issn : entry.issn) {
                    if (text.contains(issn)) {
                        JsonNode tpl = templateCache.get(entry.journalId);
                        if (tpl != null) {
                            return tpl;
                        }
                    }
                }
                for (String pattern : entry.namePatterns) {
                    if (text.contains(pattern)) {
                        JsonNode tpl = templateCache.get(entry.journalId);
                        if (tpl != null) {
                            return tpl;
                        }
                    }
                }
            }
        }
        return defaultTemplate;
    }

    private static List<JournalEntry> parseEntries(JsonNode registryNode) {
        List<JournalEntry> entries = new ArrayList<>();
        JsonNode journals = registryNode.get("journals");
        if (journals != null && journals.isArray()) {
            for (JsonNode j : journals) {
                String id = j.get("journal_id").asText();
                List<String> doiPrefixes = toStringList(j.get("doi_prefixes"));
                List<String> issn = toStringList(j.get("issn"));
                List<String> namePatterns = toStringList(j.get("name_patterns"));
                entries.add(new JournalEntry(id, doiPrefixes, issn, namePatterns));
            }
        }
        return entries;
    }

    private static List<String> toStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                list.add(node.asText());
            }
        }
        return list;
    }

    private static JsonNode loadDefaultTemplate(Map<String, JsonNode> cache) {
        if (cache.containsKey("default")) {
            return cache.get("default");
        }
        try (InputStream is = TemplateRegistry.class.getResourceAsStream(CLASSPATH_PREFIX + "default.json")) {
            if (is != null) {
                JsonNode node = MAPPER.readTree(is);
                cache.put("default", node);
                return node;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load default.json from classpath", e);
        }
        return MAPPER.createObjectNode();
    }

    private static JsonNode loadDefaultTemplateFromDir(Path dirPath, Map<String, JsonNode> cache) {
        if (cache.containsKey("default")) {
            return cache.get("default");
        }
        Path defaultPath = dirPath.resolve("default.json");
        if (Files.exists(defaultPath)) {
            try (InputStream is = Files.newInputStream(defaultPath)) {
                JsonNode node = MAPPER.readTree(is);
                cache.put("default", node);
                return node;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load default.json from directory", e);
            }
        }
        return MAPPER.createObjectNode();
    }
}
