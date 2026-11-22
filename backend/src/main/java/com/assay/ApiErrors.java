package com.assay;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String,Object>> status(ResponseStatusException e) {
    int status=e.getStatusCode().value();
    return ResponseEntity.status(status).body(Map.of("status",status,"message",e.getReason()==null?"Request failed":e.getReason(),"fieldErrors",List.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String,Object>> invalid(MethodArgumentNotValidException e) {
    List<Map<String,String>> errors=e.getBindingResult().getFieldErrors().stream().map(x->Map.of("field",x.getField(),"message",x.getDefaultMessage()==null?"Invalid value":x.getDefaultMessage())).toList();
    return ResponseEntity.badRequest().body(Map.of("status",400,"message","Invalid input","fieldErrors",errors));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String,Object>> unexpected(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status",500,"message","Request failed","fieldErrors",List.of()));
  }
}
