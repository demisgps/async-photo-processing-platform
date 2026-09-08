package com.example.photoconsumer.processamento.event;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessingEventTest {
    ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule());
    @Test void deserializesValidResultAndErrorWithoutBytes() throws Exception {
        UUID id=UUID.randomUUID(); var result=new PhotoProcessingResult(1,UUID.randomUUID(),Instant.now(),id,1,"PROCESSADA",new ProcessedObjectReference("b","n","1","image/png",2,"c",1,1));
        String json=mapper.writeValueAsString(result); assertThat(json).doesNotContain("bytes"); assertThat(mapper.readValue(json,PhotoProcessingResult.class).validateSchemaVersion()).isEqualTo(result);
        var error=new PhotoProcessingError(1,UUID.randomUUID(),Instant.now(),id,1,"ERRO_PROCESSAMENTO",new ProcessingError("INVALID","bad",false),new StorageObjectReference("o","n","1"));
        assertThat(mapper.readValue(mapper.writeValueAsString(error),PhotoProcessingError.class).validateSchemaVersion()).isEqualTo(error);
    }
    @Test void rejectsSchemaIdsAndStructuralReferences() {
        assertThatThrownBy(() -> new PhotoProcessingResult(2,UUID.randomUUID(),Instant.now(),UUID.randomUUID(),1,"PROCESSADA",new ProcessedObjectReference("b","n","1","image/png",1,"c",1,1)).validateSchemaVersion()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhotoProcessingResult(1,null,Instant.now(),null,0,"PROCESSADA",null).validateSchemaVersion()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcessedObjectReference("","n","1","image/png",1,"c",1,1).validate()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void malformedPayloadCannotDeserialize() { assertThatThrownBy(() -> mapper.readValue("{",PhotoProcessingResult.class)).isInstanceOf(Exception.class); }
}
