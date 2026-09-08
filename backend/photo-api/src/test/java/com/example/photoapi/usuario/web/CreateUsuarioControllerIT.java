package com.example.photoapi.usuario.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.exception.ApiExceptionHandler;
import com.example.photoapi.usuario.service.CreateUsuarioService;
import com.example.photoapi.usuario.web.contract.UserCreatedResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.BeforeEach;

class CreateUsuarioControllerIT {
    CreateUsuarioService service = org.mockito.Mockito.mock(CreateUsuarioService.class);
    MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void returns201WithProcessingStatus() throws Exception {
        UUID processingId = UUID.randomUUID();
        when(service.create(eq("Ana"), any())).thenReturn(new UserCreatedResponse(7, "Ana", processingId, "PROCESSANDO"));
        mvc.perform(multipart("/api/v1/usuarios").file(photo("foto")).param("nome", "Ana"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.processamentoId").value(processingId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSANDO"));
    }

    @Test void rejectsMissingRepeatedOrDifferentlyNamedFiles() throws Exception {
        mvc.perform(multipart("/api/v1/usuarios").param("nome", "Ana")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/usuarios").file(photo("foto")).file(photo("foto")).param("nome", "Ana"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/usuarios").file(photo("foto")).file(photo("avatar")).param("nome", "Ana"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/usuarios").file(photo("avatar")).param("nome", "Ana"))
                .andExpect(status().isBadRequest());
    }

    @Test void mapsValidationSizeFormatAndInternalErrors() throws Exception {
        assertMapped(HttpStatus.BAD_REQUEST, "NOME_INVALIDO", 400);
        assertMapped(HttpStatus.PAYLOAD_TOO_LARGE, "ARQUIVO_MUITO_GRANDE", 413);
        assertMapped(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FORMATO_NAO_SUPORTADO", 415);
        when(service.create(eq("Falha"), any())).thenThrow(new IllegalStateException("storage"));
        mvc.perform(multipart("/api/v1/usuarios").file(photo("foto")).param("nome", "Falha"))
                .andExpect(status().isInternalServerError());
    }

    private void assertMapped(HttpStatus status, String code, int expected) throws Exception {
        String name = code;
        when(service.create(eq(name), any())).thenThrow(new ApiException(status, code, "erro"));
        mvc.perform(multipart("/api/v1/usuarios").file(photo("foto")).param("nome", name))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.codigo").value(code));
    }

    private MockMultipartFile photo(String field) {
        return new MockMultipartFile(field, "x.png", "image/png", new byte[]{1});
    }
}
