package com.ontotrace.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一 ProblemDetail 错误响应。
 *
 * @author hanbd
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 业务冲突，例如修订号过期或幂等冲突。
     */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /**
     * 资源不存在。
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 配置或前置条件不满足。
     */
    public static class UnprocessableException extends RuntimeException {
        public UnprocessableException(String message) {
            super(message);
        }
    }

    /**
     * 处理校验失败。
     *
     * @param ex 校验异常
     * @return 400 问题详情
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("校验失败");
        problem.setDetail(ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
        return problem;
    }

    /**
     * 处理错误凭据。
     *
     * @param ex 认证异常
     * @return 401 问题详情
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail badCredentials(BadCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("登录失败");
        problem.setDetail("用户名或密码错误");
        return problem;
    }

    /**
     * 处理无权限。
     *
     * @param ex 权限异常
     * @return 403 问题详情
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("无权访问");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * 处理资源不存在。
     *
     * @param ex 不存在异常
     * @return 404 问题详情
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("资源不存在");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * 处理冲突。
     *
     * @param ex 冲突异常
     * @return 409 问题详情
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("资源冲突");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * 处理无法继续的业务条件。
     *
     * @param ex 业务异常
     * @return 422 问题详情
     */
    @ExceptionHandler(UnprocessableException.class)
    public ProblemDetail unprocessable(UnprocessableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("无法处理");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * 处理非法参数。
     *
     * @param ex 参数异常
     * @return 400 问题详情
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail illegal(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("请求无效");
        problem.setDetail(ex.getMessage());
        return problem;
    }
}
