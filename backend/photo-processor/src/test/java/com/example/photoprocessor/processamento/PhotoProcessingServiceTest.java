package com.example.photoprocessor.processamento;

import static org.mockito.Mockito.*;
import com.example.photoprocessor.event.*;
import com.example.photoprocessor.imagem.*;
import com.example.photoprocessor.storage.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoProcessingServiceTest {
    @Test void orchestratesSuccessInOrder() {
        var storage=mock(PhotoStorage.class); var transformer=mock(ImageTransformer.class); var publisher=mock(ProcessingEventPublisher.class);
        UUID id=UUID.randomUUID(); var event=event(id); var original=new OriginalPhoto(new byte[]{1},new StorageObjectReference("original",event.name(),"1"));
        var image=new TransformedImage(new byte[]{2},"image/jpeg","jpg",1,1,"sum"); var ref=new ProcessedObjectReference("processed","1/x/arquivo.jpg","2","image/jpeg",1,"sum",1,1);
        when(storage.download(event)).thenReturn(original); when(transformer.transform(any())).thenReturn(image); when(storage.save(1,id,image,original.reference())).thenReturn(ref);
        new PhotoProcessingService(storage,transformer,publisher).process(event);
        var order=inOrder(storage,transformer,publisher); order.verify(storage).download(event); order.verify(transformer).transform(any()); order.verify(storage).save(1,id,image,original.reference());
        order.verify(publisher).publish(argThat(e -> e instanceof PhotoProcessingResult r && r.status().equals("PROCESSADA")));
    }
    @Test void publishesDefinitiveFunctionalError() {
        var storage=mock(PhotoStorage.class); var transformer=mock(ImageTransformer.class); var publisher=mock(ProcessingEventPublisher.class); UUID id=UUID.randomUUID(); var event=event(id);
        when(storage.download(event)).thenReturn(new OriginalPhoto(new byte[]{1},new StorageObjectReference("original",event.name(),"1")));
        when(transformer.transform(any())).thenThrow(new FunctionalProcessingException("INVALID_IMAGE_CONTENT","bad"));
        new PhotoProcessingService(storage,transformer,publisher).process(event);
        verify(publisher).publish(argThat(e -> e instanceof PhotoProcessingError error && !error.error().transientFailure()));
    }
    private StorageFinalizedEvent event(UUID id) { return new StorageFinalizedEvent("original","1/"+id+"/arquivo.jpg","1","image/jpeg",1,"sum",Map.of("usuarioId","1","processamentoId",id.toString())); }
}
