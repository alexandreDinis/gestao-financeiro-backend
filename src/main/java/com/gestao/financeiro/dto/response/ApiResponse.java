package com.gestao.financeiro.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wrapper padrão para todas as respostas da API.
 * Formato: { "data": {}, "meta": {}, "errors": [] }
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private T data;
    private Meta meta;
    private List<ApiError> errors;

    private ApiResponse() {}

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> created(T data) {
        return ok(data);
    }

    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>();
    }

    public static <T> ApiResponse<List<T>> ok(Page<T> page) {
        ApiResponse<List<T>> response = new ApiResponse<>();
        response.setData(page.getContent());
        response.setMeta(Meta.fromPage(page));
        return response;
    }

    public static <T> ApiResponse<T> error(List<ApiError> errors) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setErrors(errors);
        return response;
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return error(List.of(new ApiError(code, message, null)));
    }

    public static <T> ApiResponse<T> error(String code, String message, String field) {
        return error(List.of(new ApiError(code, message, field)));
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;

        public static Meta fromPage(Page<?> page) {
            Meta meta = new Meta();
            meta.setPage(page.getNumber());
            meta.setSize(page.getSize());
            meta.setTotalElements(page.getTotalElements());
            meta.setTotalPages(page.getTotalPages());
            return meta;
        }
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiError {
        private final String code;
        private final String message;
        private final String field;

        public ApiError(String code, String message, String field) {
            this.code = code;
            this.message = message;
            this.field = field;
        }
    }
}
