package com.munte.KickOffBet.util;

import com.munte.KickOffBet.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageableValidatorTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "amount");

    @Test
    void unsortedPageIsAccepted() {
        assertThatCode(() -> PageableValidator.validate(PageRequest.of(0, 10), ALLOWED)).doesNotThrowAnyException();
    }

    @Test
    void sortingByAllowedFieldsIsAccepted() {
        PageRequest page = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("amount")));

        assertThatCode(() -> PageableValidator.validate(page, ALLOWED)).doesNotThrowAnyException();
    }

    @Test
    void sortingByAnyOtherFieldIsRejected() {
        PageRequest page = PageRequest.of(0, 10, Sort.by("createdAt", "user.password"));

        assertThatThrownBy(() -> PageableValidator.validate(page, ALLOWED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("user.password");
    }

    @Test
    void fieldNamesAreCaseSensitive() {
        assertThatThrownBy(() -> PageableValidator.validate(PageRequest.of(0, 5, Sort.by("CreatedAt")), ALLOWED))
                .isInstanceOf(BusinessException.class);
    }
}
