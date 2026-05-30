package com.taoking.spring3.catalog.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

public final class CatalogNativeRuntimeHints implements RuntimeHintsRegistrar {

    private static final TypeReference HIBERNATE_VALIDATOR_LOGGER =
            TypeReference.of("org.hibernate.validator.internal.util.logging.Log_$logger");
    private static final TypeReference HIBERNATE_VALIDATOR_MESSAGES =
            TypeReference.of("org.hibernate.validator.internal.util.logging.Messages_$bundle");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(HIBERNATE_VALIDATOR_LOGGER, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        hints.reflection().registerType(
                HIBERNATE_VALIDATOR_MESSAGES,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }
}
