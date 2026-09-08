package com.example.photoapi.processamento.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.exception.ApiExceptionHandler;
import com.example.photoapi.processamento.service.ProcessingQueryService;
import com.example.photoapi.usuario.web.contract.ProcessingResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProcessingQueryControllerIT {
    @Test
    void returns200And404() throws Exception {
        var service = mock(ProcessingQueryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProcessingController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        UUID found = UUID.randomUUID();
        when(service.find(found)).thenReturn(new ProcessingResponse(found, 1, "PROCESSANDO", null, null));
        mvc.perform(get("/api/v1/processamentos/{id}", found)).andExpect(status().isOk())
                .andExpect(jsonPath("$.processamentoId").value(found.toString()));
        UUID absent = UUID.randomUUID();
        when(service.find(absent)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "PROCESSAMENTO_NAO_ENCONTRADO", "ausente"));
        mvc.perform(get("/api/v1/processamentos/{id}", absent)).andExpect(status().isNotFound());
    }
}
