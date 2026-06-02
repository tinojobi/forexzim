package com.forexzim.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(assignableTypes = AdminController.class)
public class AdminExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException ex, Model model) {
        model.addAttribute("statusCode", 404);
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("activePage", "");
        return "admin/error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleForbidden(Model model) {
        model.addAttribute("statusCode", 403);
        model.addAttribute("message", "You don't have permission to access this page.");
        model.addAttribute("activePage", "");
        return "admin/error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(Model model) {
        model.addAttribute("statusCode", 404);
        model.addAttribute("message", "That page doesn't exist.");
        model.addAttribute("activePage", "");
        return "admin/error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(Exception ex, Model model) {
        model.addAttribute("statusCode", 500);
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("activePage", "");
        return "admin/error";
    }
}
