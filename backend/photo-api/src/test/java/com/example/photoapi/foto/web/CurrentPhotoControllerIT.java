package com.example.photoapi.foto.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.exception.ApiExceptionHandler;
import com.example.photoapi.foto.service.CurrentPhoto;
import com.example.photoapi.foto.service.CurrentPhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CurrentPhotoControllerIT {
    @Test
    void servesPersistedPhotoAndMapsTemporaryOrTerminalAbsence() throws Exception {
        var service = mock(CurrentPhotoService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PhotoController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        when(service.find(1)).thenReturn(new CurrentPhoto(new byte[]{1, 2}, "image/png"));
        mvc.perform(get("/api/v1/usuarios/1/foto")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png")).andExpect(content().bytes(new byte[]{1, 2}));
        when(service.find(2)).thenThrow(new ApiException(HttpStatus.CONFLICT, "FOTO_TEMPORARIAMENTE_INDISPONIVEL", "ativa"));
        mvc.perform(get("/api/v1/usuarios/2/foto")).andExpect(status().isConflict());
        when(service.find(3)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "FOTO_NAO_ENCONTRADA", "terminal"));
        mvc.perform(get("/api/v1/usuarios/3/foto")).andExpect(status().isNotFound());
    }
}
