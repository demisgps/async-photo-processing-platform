package com.example.photoprocessor.processamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.example.photoprocessor.config.ProcessorProperties;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import java.net.URI;
import org.junit.jupiter.api.Test;

class StorageCloudEventTest {
    @Test void delegatesValidFinalizedEventAndRejectsInvalidStructure() throws Exception {
        var service = mock(PhotoProcessingService.class);
        var props = new ProcessorProperties(URI.create("http://localhost"), "project", "original", "processed", "t", 25_000_000);
        var function = new PhotoProcessorFunction(props, service);
        String id = java.util.UUID.randomUUID().toString();
        function.accept(event("{\"bucket\":\"original\",\"name\":\"1/"+id+"/arquivo.jpg\",\"generation\":\"2\",\"contentType\":\"image/jpeg\",\"size\":2,\"metadata\":{\"usuarioId\":\"1\",\"processamentoId\":\""+id+"\"}}"));
        verify(service).process(any());
        assertThatThrownBy(() -> function.accept(event("{\"bucket\":\"original\",\"name\":\"bad\",\"generation\":\"\",\"metadata\":{}}")))
                .isInstanceOf(Exception.class);
    }
    @Test void ignoresOtherTypesAndBuckets() throws Exception {
        var service=mock(PhotoProcessingService.class); var props=new ProcessorProperties(URI.create("http://localhost"),"project","original","processed","t",1);
        var fn=new PhotoProcessorFunction(props,service); CloudEvent other=mock(CloudEvent.class); when(other.getType()).thenReturn("other"); fn.accept(other);
        fn.accept(event("{\"bucket\":\"processed\",\"name\":\"x\",\"generation\":\"1\",\"metadata\":{}}")); verifyNoInteractions(service);
    }
    static CloudEvent event(String json) {
        CloudEvent event=mock(CloudEvent.class); when(event.getType()).thenReturn(PhotoProcessorFunction.FINALIZED);
        CloudEventData data = mock(CloudEventData.class);
        when(data.toBytes()).thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(event.getData()).thenReturn(data); return event;
    }
}
