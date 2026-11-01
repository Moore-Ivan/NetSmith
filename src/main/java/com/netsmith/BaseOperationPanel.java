package com.netsmith;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class BaseOperationPanel extends JPanel {

    protected final Consumer<String> statusCallback;

    public BaseOperationPanel(Consumer<String> statusCallback) {
        this.statusCallback = statusCallback;
    }

    protected void bindEnterKey(JTextField field, JButton button) {
        field.addActionListener(e -> button.doClick());
    }

    protected void handleError(Exception ex, ErrorIndicator errorIndicator, JComponent component) {
        errorIndicator.setError(component, ex.getMessage());
        statusCallback.accept("错误: " + ex.getMessage());
    }

    protected void handleValidationError(Exception ex, ErrorIndicator defaultError, JComponent defaultField,
                                         Map<ValidationError.Field, ErrorBinding> fieldBindings) {
        if (ex instanceof ValidationError) {
            ValidationError ve = (ValidationError) ex;
            ErrorBinding binding = fieldBindings.get(ve.getField());
            if (binding != null) {
                binding.errorIndicator.setError(binding.component, ve.getMessage());
            } else {
                defaultError.setError(defaultField, ve.getMessage());
            }
        } else {
            defaultError.setError(defaultField, ex.getMessage());
        }
        statusCallback.accept("错误: " + ex.getMessage());
    }

    protected void handleValidationError(Exception ex, ErrorIndicator cidrError, ErrorIndicator otherError,
                                         JComponent cidrField, JComponent otherField,
                                         ValidationError.Field otherFieldType) {
        Map<ValidationError.Field, ErrorBinding> bindings = new HashMap<>();
        bindings.put(otherFieldType, new ErrorBinding(otherError, otherField));
        handleValidationError(ex, cidrError, cidrField, bindings);
    }

    protected void handleValidationError(Exception ex, ErrorIndicator cidrError,
                                         ErrorIndicator error2, ErrorIndicator error3,
                                         JComponent cidrField, JComponent field2, JComponent field3,
                                         ValidationError.Field field2Type, ValidationError.Field field3Type) {
        Map<ValidationError.Field, ErrorBinding> bindings = new HashMap<>();
        bindings.put(field2Type, new ErrorBinding(error2, field2));
        bindings.put(field3Type, new ErrorBinding(error3, field3));
        handleValidationError(ex, cidrError, cidrField, bindings);
    }

    protected void handleValidationError(Exception ex, ErrorIndicator cidrError, ErrorIndicator otherError,
                                         JComponent cidrField, JComponent otherField,
                                         ValidationError.Field... otherFieldTypes) {
        Map<ValidationError.Field, ErrorBinding> bindings = new HashMap<>();
        for (ValidationError.Field type : otherFieldTypes) {
            bindings.put(type, new ErrorBinding(otherError, otherField));
        }
        handleValidationError(ex, cidrError, cidrField, bindings);
    }

    protected void clearErrors(ErrorIndicator... indicators) {
        for (ErrorIndicator indicator : indicators) {
            indicator.clearError();
        }
    }

    protected static class ErrorBinding {
        final ErrorIndicator errorIndicator;
        final JComponent component;

        public ErrorBinding(ErrorIndicator errorIndicator, JComponent component) {
            this.errorIndicator = errorIndicator;
            this.component = component;
        }
    }
}