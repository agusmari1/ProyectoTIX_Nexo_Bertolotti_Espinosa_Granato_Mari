package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uy.edu.um.nexo.mnatural.dto.ApiErrorDTO;
import uy.edu.um.nexo.mnatural.exception.InvalidApiRequestException;

// Los pedidos inválidos a la API se responden con un 400 y un mensaje claro en JSON
@RestControllerAdvice(assignableTypes = PublicApiController.class)
public class PublicApiExceptionHandler {

    @ExceptionHandler(InvalidApiRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO invalidRequest(InvalidApiRequestException e) {
        return new ApiErrorDTO(e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO missingParameter(MissingServletRequestParameterException e) {
        return new ApiErrorDTO("Falta el parámetro obligatorio '" + e.getParameterName() + "'.");
    }

    // Por ejemplo, una fecha mal escrita o un departamento que no existe
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO invalidParameter(MethodArgumentTypeMismatchException e) {
        return new ApiErrorDTO("El valor de '" + e.getName() + "' no es válido.");
    }
}
