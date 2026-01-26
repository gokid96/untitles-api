package com.untitles.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {


    private String status;
    private int code;
    private String message;
    private T data;


    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", 200, null, data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>("success", 200, null, null);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>("error", code, message, null);
    }

}
