package org.opendataloader.pdf.processors.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PaperValidatorTest {

    @Test
    void testValidTitle() {
        assertTrue(PaperValidator.isValidTitle("Speech Processing in Children"));
        assertTrue(PaperValidator.isValidTitle("딥러닝 기반 자연어 처리 연구"));
    }

    @Test
    void testInvalidTitleCategoryLabel() {
        assertFalse(PaperValidator.isValidTitle("Original Article"));
        assertFalse(PaperValidator.isValidTitle("Review Article"));
        assertFalse(PaperValidator.isValidTitle("Case Report"));
        assertFalse(PaperValidator.isValidTitle("Brief Report"));
        assertFalse(PaperValidator.isValidTitle("Short Communication"));
    }

    @Test
    void testInvalidTitleTooShort() {
        assertFalse(PaperValidator.isValidTitle("Hi"));
        assertFalse(PaperValidator.isValidTitle(null));
        assertFalse(PaperValidator.isValidTitle(""));
    }

    @Test
    void testValidAuthor() {
        assertTrue(PaperValidator.isValidAuthor("홍길동"));
        assertTrue(PaperValidator.isValidAuthor("John Smith"));
        assertTrue(PaperValidator.isValidAuthor("Deok Gi Chae"));
    }

    @Test
    void testInvalidAuthorDateText() {
        assertFalse(PaperValidator.isValidAuthor("Received: 1 August"));
        assertFalse(PaperValidator.isValidAuthor("2025. Revised: 6 September"));
        assertFalse(PaperValidator.isValidAuthor("Accepted: 6 September"));
        assertFalse(PaperValidator.isValidAuthor("Published: March 2025"));
    }

    @Test
    void testInvalidAuthorTooShort() {
        assertFalse(PaperValidator.isValidAuthor("A"));
        assertFalse(PaperValidator.isValidAuthor(null));
        assertFalse(PaperValidator.isValidAuthor("123"));
    }

    @Test
    void testValidDoi() {
        assertTrue(PaperValidator.isValidDoi("10.21849/cacd.2025.10.3.1"));
        assertTrue(PaperValidator.isValidDoi("10.26844/ksepe.2025.31.4.1"));
    }

    @Test
    void testInvalidDoi() {
        assertFalse(PaperValidator.isValidDoi("not-a-doi"));
        assertFalse(PaperValidator.isValidDoi(null));
        assertFalse(PaperValidator.isValidDoi(""));
    }

    @Test
    void testValidAbstract() {
        assertTrue(PaperValidator.isValidAbstract(
            "This study examines the speech processing abilities of children with phonological disorders compared to typically developing children."));
    }

    @Test
    void testInvalidAbstractTooShort() {
        assertFalse(PaperValidator.isValidAbstract("Short."));
        assertFalse(PaperValidator.isValidAbstract(null));
    }

    @Test
    void testValidYear() {
        assertTrue(PaperValidator.isValidYear(2025));
        assertTrue(PaperValidator.isValidYear(1990));
    }

    @Test
    void testInvalidYear() {
        assertFalse(PaperValidator.isValidYear(1800));
        assertFalse(PaperValidator.isValidYear(2099));
        assertFalse(PaperValidator.isValidYear(null));
    }
}
