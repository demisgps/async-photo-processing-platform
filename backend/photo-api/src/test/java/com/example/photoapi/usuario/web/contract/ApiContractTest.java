package com.example.photoapi.usuario.web.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiContractTest {
    @Test
    void errorFieldsAreImmutableAndNullBecomesEmpty() {
        var empty = new ApiErrorResponse(400, "INVALID", "inválido", Instant.EPOCH, null, null);
        assertEquals(0, empty.campos().size());
        var source = new ArrayList<FieldErrorResponse>();
        source.add(new FieldErrorResponse("nome", "obrigatório"));
        var response = new ApiErrorResponse(400, "INVALID", "inválido", Instant.EPOCH,
                UUID.randomUUID(), source);
        source.clear();
        assertEquals(1, response.campos().size());
        assertThrows(UnsupportedOperationException.class,
                () -> response.campos().add(new FieldErrorResponse("foto", "obrigatória")));
    }

    @Test
    void responseRecordsMatchPublicContractShape() {
        UUID id = UUID.randomUUID();
        var photo = new CurrentPhotoSummaryResponse(true, id, "PERSISTIDA");
        assertEquals(1, new UserSummaryResponse(1, "Nome", photo).id());
        assertEquals(id, new UserCreatedResponse(1, "Nome", id, "PROCESSANDO").processamentoId());
        assertEquals(1, new ProcessingAcceptedResponse(id, 1, "PROCESSANDO").usuarioId());
        assertEquals("ERRO", new ProcessingResponse(id, 1, "ERRO_PROCESSAMENTO", "ERRO", "detalhe").erroCodigo());
        assertEquals("Nome", new UserDetailResponse(1, "Nome", photo, Instant.EPOCH, Instant.EPOCH).nome());
        assertEquals("Nome", new CreateUserRequest("Nome").nome());
        assertEquals("Novo", new UpdateUserNameRequest("Novo").nome());
    }
}

