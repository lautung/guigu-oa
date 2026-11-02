package com.lautung.oa.framework.config.exception;

import com.lautung.oa.common.result.Result;
import com.lautung.oa.common.validation.ValidationMessages;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;


@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //全局异常处理，执行的方法
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result error(Exception e) {
        log.error("Unhandled request exception", e);
        return Result.fail().message("执行全局异常处理...");
    }

    //容器级 404：未知路径返回真实状态码，不再伪装成业务失败（#145）
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public Result error(NoResourceFoundException e) {
        log.info("No handler for {} {}", e.getHttpMethod(), e.getResourcePath());
        return Result.fail().message("请求的资源不存在");
    }

    //容器级 405：方法不匹配返回真实状态码（#145）
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ResponseBody
    public Result error(HttpRequestMethodNotSupportedException e) {
        log.info("Method not allowed: {}", e.getMessage());
        return Result.fail().message("请求方法不支持");
    }

    //参数校验失败：仍走业务信封，消息按字段名排序后连接（ADR 0033）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public Result error(MethodArgumentNotValidException e) {
        String message = ValidationMessages.join(e.getBindingResult().getAllErrors().stream()
                .sorted(Comparator.comparing(error -> error instanceof FieldError fieldError
                        ? fieldError.getField()
                        : ""))
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList());
        log.info("Request validation failed: {}", message);
        return Result.fail().message(message);
    }

    //缺体或畸形 JSON：不落到泛化的全局异常文案
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public Result error(HttpMessageNotReadableException e) {
        log.info("Unreadable request body: {}", e.getMessage());
        return Result.fail().message("请求体格式无效");
    }

    //特定异常处理
    @ExceptionHandler(ArithmeticException.class)
    @ResponseBody
    public Result error(ArithmeticException e) {
        log.error("Arithmetic request exception", e);
        return Result.fail().message("执行特定异常处理...");
    }

    //自定义异常处理
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public Result error(BusinessException e) {
        log.warn("Business request failed with code {}", e.getCode(), e);
        return Result.fail().code(e.getCode()).message(e.getMsg());
    }

    /**
     * spring security异常
     * @param e
     * @return
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public Result error(AccessDeniedException e) throws AccessDeniedException {
        return Result.fail().code(205).message("没有操作权限");
    }
}
