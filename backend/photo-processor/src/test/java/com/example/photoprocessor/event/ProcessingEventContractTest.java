package com.example.photoprocessor.event;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessingEventContractTest {
    @Test void resultAndErrorContainReferencesButNeverImageBytes() throws Exception {
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule()); UUID id=UUID.randomUUID();
        String result=mapper.writeValueAsString(new PhotoProcessingResult(1,UUID.randomUUID(),Instant.now(),id,1,"PROCESSADA",
                new ProcessedObjectReference("b","1/"+id+"/arquivo.jpg","1","image/jpeg",2,"c",1,1)));
        String error=mapper.writeValueAsString(new PhotoProcessingError(1,UUID.randomUUID(),Instant.now(),id,1,"ERRO_PROCESSAMENTO",
                new ProcessingError("INVALID_IMAGE_CONTENT","bad",false),new StorageObjectReference("o","n","1")));
        assertThat(result).doesNotContain("bytes","imageData").contains("processedObject");
        assertThat(error).doesNotContain("bytes","stackTrace").contains("\"transient\":false");
    }
    @Test void processorClasspathHasNoMysqlSpringBootOrActuator() {
        assertThat(classPresent("com.mysql.cj.jdbc.Driver")).isFalse(); assertThat(classPresent("org.springframework.boot.SpringApplication")).isFalse();
        assertThat(classPresent("org.springframework.boot.actuate.health.Health")).isFalse();
    }
    private boolean classPresent(String name) { try { Class.forName(name); return true; } catch (ClassNotFoundException ignored) { return false; } }
}
