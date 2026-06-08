package com.treeeducation.ioas.dataops;

import com.treeeducation.ioas.common.ApiResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.treeeducation.ioas.dataops")
public class DataOperationExceptionAdvice {
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        String message = ex.getMessage() == null ? "数据已存在" : ex.getMessage();
        if (message.contains("uk_data_operation_package_display_name") || message.contains("display_name")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, "主题包名字已存在，请换一个运营/媒体/日期组合"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, "数据已存在，请勿重复提交"));
    }
}
