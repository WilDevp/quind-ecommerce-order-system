package com.quind.ecommerce.order.domain.exception;

/**
 * Excepción lanzada cuando se intenta crear una orden sin items.
 * Una orden debe tener al menos un item para ser válida.
 */
public class EmptyOrderException extends DomainException {

    public EmptyOrderException() {
        super("No se puede crear una orden sin items");
    }
}
