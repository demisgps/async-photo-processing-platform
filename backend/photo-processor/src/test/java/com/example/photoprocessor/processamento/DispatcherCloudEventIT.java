package com.example.photoprocessor.processamento;

import static org.mockito.Mockito.*;
import com.example.photoprocessor.config.ProcessorProperties;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatcherCloudEventIT {
    @Test void dispatcherCompatibleStructuredDataReachesFunctionWithoutBusinessTranslation() throws Exception {
        UUID id=UUID.randomUUID(); var service=mock(PhotoProcessingService.class);
        var function=new PhotoProcessorFunction(new ProcessorProperties(URI.create("http://localhost"),"fotos-usuarios-original","processed","p","t",25_000_000),service);
        String json="{\"bucket\":\"fotos-usuarios-original\",\"name\":\"9/"+id+"/arquivo.png\",\"generation\":\"4\",\"contentType\":\"image/png\",\"size\":123,\"crc32c\":\"sum\",\"metadata\":{\"usuarioId\":\"9\",\"processamentoId\":\""+id+"\"}}";
        function.accept(StorageCloudEventTest.event(json));
        verify(service).process(argThat(e -> e.usuarioId()==9 && e.processamentoId().equals(id) && e.generation().equals("4")));
    }
}
