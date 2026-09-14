package io.jettra.ee.jakarta.validation;

import io.jettra.rules.core.JettraRulesEngine;
import io.jettra.rules.core.JettraComputeEngine;
import io.jettra.rules.core.RuleResult;
import jakarta.validation.constraints.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Motor de validación unificado para JettraEE.
 * Soporta de forma transparente:
 * 1. Anotaciones de Jakarta Validation 3.1 (jakarta.validation.constraints.*)
 * 2. Anotaciones y motor de JettraRules (io.jettra.rules.*)
 * 3. Cálculo de campos derivados mediante JettraComputeEngine
 */
public class ValidationEngine {

    public static class ValidationError {
        private final String property;
        private final String message;
        private final Object invalidValue;

        public ValidationError(String property, String message, Object invalidValue) {
            this.property = property;
            this.message = message;
            this.invalidValue = invalidValue;
        }

        public String getProperty() { return property; }
        public String getMessage() { return message; }
        public Object getInvalidValue() { return invalidValue; }

        @Override
        public String toString() {
            return property + ": " + message + " (valor: '" + invalidValue + "')";
        }
    }

    /**
     * Valida una entidad aplicando validaciones Jakarta Validation y ejecutando JettraRules.
     */
    public static List<ValidationError> validate(Object entity) {
        List<ValidationError> errors = new ArrayList<>();
        if (entity == null) {
            return errors;
        }

        // 1. Ejecutar JettraComputeEngine si la entidad tiene campos computables
        try {
            JettraComputeEngine.compute(entity);
        } catch (Throwable ignored) {}

        // 2. Validar restricciones de campos (Jakarta Validation y Jettra)
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object val = field.get(entity);
                    validateField(field, val, errors);
                } catch (IllegalAccessException e) {
                    // ignore
                }
            }
            current = current.getSuperclass();
        }

        // 3. Ejecutar motor de reglas JettraRulesEngine si tiene reglas anotadas
        try {
            List<RuleResult> ruleResults = JettraRulesEngine.validate(entity);
            if (ruleResults != null) {
                for (RuleResult rr : ruleResults) {
                    if (rr != null && !rr.isValid()) {
                        errors.add(new ValidationError(
                                rr.getField() != null ? rr.getField() : "rule",
                                rr.getMessage() != null ? rr.getMessage() : "Regla de negocio no satisfecha",
                                null
                        ));
                    }
                }
            }
        } catch (Throwable ignored) {}

        return errors;
    }

    private static void validateField(Field field, Object val, List<ValidationError> errors) {
        String name = field.getName();

        // @NotNull
        if (field.isAnnotationPresent(NotNull.class) && val == null) {
            NotNull ann = field.getAnnotation(NotNull.class);
            errors.add(new ValidationError(name, ann.message() != null && !ann.message().contains("{") ? ann.message() : "No debe ser nulo", null));
        }

        // @NotBlank
        if (field.isAnnotationPresent(NotBlank.class)) {
            NotBlank ann = field.getAnnotation(NotBlank.class);
            if (val == null || val.toString().trim().isEmpty()) {
                errors.add(new ValidationError(name, ann.message() != null && !ann.message().contains("{") ? ann.message() : "No debe estar en blanco", val));
            }
        }

        // @NotEmpty
        if (field.isAnnotationPresent(NotEmpty.class)) {
            NotEmpty ann = field.getAnnotation(NotEmpty.class);
            if (val == null) {
                errors.add(new ValidationError(name, ann.message() != null && !ann.message().contains("{") ? ann.message() : "No debe estar vacío", val));
            } else if (val instanceof String s && s.isEmpty()) {
                errors.add(new ValidationError(name, ann.message() != null && !ann.message().contains("{") ? ann.message() : "No debe estar vacío", val));
            } else if (val instanceof Collection<?> c && c.isEmpty()) {
                errors.add(new ValidationError(name, ann.message() != null && !ann.message().contains("{") ? ann.message() : "No debe estar vacío", val));
            } else if (val instanceof Map<?, ?> m && m.isEmpty()) {
                errors.add(new ValidationError(name, ann.message() != null && !ann.message().contains("{") ? ann.message() : "No debe estar vacío", val));
            }
        }

        // @Size
        if (field.isAnnotationPresent(Size.class) && val != null) {
            Size ann = field.getAnnotation(Size.class);
            int size = -1;
            if (val instanceof String s) size = s.length();
            else if (val instanceof Collection<?> c) size = c.size();
            else if (val instanceof Map<?, ?> m) size = m.size();

            if (size != -1 && (size < ann.min() || size > ann.max())) {
                errors.add(new ValidationError(name, "El tamaño debe estar entre " + ann.min() + " y " + ann.max(), val));
            }
        }

        // @Min
        if (field.isAnnotationPresent(Min.class) && val instanceof Number n) {
            Min ann = field.getAnnotation(Min.class);
            if (n.longValue() < ann.value()) {
                errors.add(new ValidationError(name, "El valor debe ser mayor o igual a " + ann.value(), val));
            }
        }

        // @Max
        if (field.isAnnotationPresent(Max.class) && val instanceof Number n) {
            Max ann = field.getAnnotation(Max.class);
            if (n.longValue() > ann.value()) {
                errors.add(new ValidationError(name, "El valor debe ser menor o igual a " + ann.value(), val));
            }
        }

        // @Email
        if (field.isAnnotationPresent(Email.class) && val instanceof String s && !s.isEmpty()) {
            if (!s.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                errors.add(new ValidationError(name, "Debe ser una dirección de correo válida", val));
            }
        }

        // @jakarta.validation.constraints.Pattern
        if (field.isAnnotationPresent(jakarta.validation.constraints.Pattern.class) && val instanceof String s) {
            jakarta.validation.constraints.Pattern ann = field.getAnnotation(jakarta.validation.constraints.Pattern.class);
            if (!Pattern.compile(ann.regexp()).matcher(s).matches()) {
                errors.add(new ValidationError(name, "El formato no cumple con el patrón requerido: " + ann.regexp(), val));
            }
        }
    }
}
