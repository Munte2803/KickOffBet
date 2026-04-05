package com.munte.KickOffBet.util;

import com.munte.KickOffBet.exceptions.BusinessException;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public final class PageableValidator {

    private PageableValidator() {}

    public static void validate(Pageable pageable, Set<String> allowedFields) {
        pageable.getSort().forEach(order -> {
            if (!allowedFields.contains(order.getProperty())) {
                throw new BusinessException(
                        "Sorting by '" + order.getProperty() + "' is not allowed"
                );
            }
        });
    }
}