package org.opendataloader.pdf.processors.paper;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRegistryTest {

    @Test
    void testLoadDefaultRegistry() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        assertThat(registry).isNotNull();
    }

    @Test
    void testFindByDoiPrefix() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        JsonNode template = registry.findTemplate("10.21849/cacd.2025.10.3.1");
        assertThat(template.get("journal_id").asText()).isEqualTo("cacd");
    }

    @Test
    void testFindByNamePattern() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        JsonNode template = registry.findTemplateByText("이 논문은 한국초등체육학회지에 실린 연구입니다.");
        assertThat(template.get("journal_id").asText()).isEqualTo("ksepe");
    }

    @Test
    void testUnknownJournalReturnsDefault() {
        TemplateRegistry registry = TemplateRegistry.loadDefault();
        JsonNode template = registry.findTemplate("10.9999/unknown");
        assertThat(template.get("journal_id").asText()).isEqualTo("default");
    }
}
