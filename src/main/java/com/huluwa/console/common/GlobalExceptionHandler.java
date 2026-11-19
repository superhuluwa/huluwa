package com.huluwa.console.common;

import com.huluwa.console.service.DongPianSearchService.DongPianException;
import com.huluwa.console.service.DongPianSearchService.DongPianLineUnavailableException;
import com.huluwa.console.service.huangguo.HuangguoException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                .body(ApiResult.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(DongPianLineUnavailableException.class)
    public ResponseEntity<ApiResult<Void>> handleDongPianLineUnavailable(DongPianLineUnavailableException ex) {
        return ResponseEntity.unprocessableEntity().body(ApiResult.fail(422, ex.getMessage()));
    }

    /** 上游懂片帝的会话、权限和限流原因直接交给客户端，避免被通用 500 提示覆盖。 */
    @ExceptionHandler(DongPianException.class)
    public ResponseEntity<ApiResult<Void>> handleDongPian(DongPianException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResult.fail(502, ex.getMessage()));
    }

    /** 黄果源站在网络、加密握手或播放响应异常时返回 502，客户端可直接展示原因。 */
    @ExceptionHandler(HuangguoException.class)
    public ResponseEntity<ApiResult<Void>> handleHuangguo(HuangguoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResult.fail(502, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return badRequest(firstError(ex.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResult<Void>> handleBind(BindException ex) {
        return badRequest(firstError(ex.getBindingResult()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("参数校验失败");
        return badRequest(message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 uri={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(500, "服务异常，请稍后重试"));
    }

    private static String firstError(BindingResult result) {
        return result.getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
    }

    private static ResponseEntity<ApiResult<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(ApiResult.fail(400, message));
    }
}