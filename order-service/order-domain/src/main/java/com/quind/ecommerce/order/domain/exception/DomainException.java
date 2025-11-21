package com.quind.ecommerce.order.domain.exception;

/**
 * Clase base para todas las excepciones del dominio.
 *
 * Usamos una jerarquía de excepciones para poder capturar todas las excepciones
 * de dominio en la capa de aplicación/infraestructura y transformarlas apropiadamente.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
