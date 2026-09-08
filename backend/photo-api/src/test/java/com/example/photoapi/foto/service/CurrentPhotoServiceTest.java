package com.example.photoapi.foto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.photoapi.exception.ApiException; import com.example.photoapi.processamento.domain.*; import com.example.photoapi.processamento.repository.PhotoProcessingRepository; import com.example.photoapi.usuario.domain.User; import com.example.photoapi.usuario.repository.UserRepository;
import java.util.*; import org.junit.jupiter.api.Test;

class CurrentPhotoServiceTest {
 @Test void returnsCurrentEvenWithActiveReplacementAndDistinguishes409From404() {
  var users=mock(UserRepository.class); var processings=mock(PhotoProcessingRepository.class); var service=new CurrentPhotoService(users,processings);
  UUID currentId=UUID.randomUUID(); User withCurrent=mock(User.class); PhotoProcessing current=mock(PhotoProcessing.class);
  when(withCurrent.getCurrentPhotoProcessingId()).thenReturn(currentId); when(users.findById(1L)).thenReturn(Optional.of(withCurrent)); when(processings.findById(currentId)).thenReturn(Optional.of(current));
  when(current.getStatus()).thenReturn(ProcessingStatus.PERSISTIDA); when(current.getProcessedImage()).thenReturn(new byte[]{5}); when(current.getContentType()).thenReturn("image/jpeg");
  assertThat(service.find(1).bytes()).containsExactly(5);
  User without=mock(User.class); when(users.findById(2L)).thenReturn(Optional.of(without)); when(processings.existsByUserIdAndStatusIn(eq(2L),any())).thenReturn(true);
  assertThatThrownBy(() -> service.find(2)).isInstanceOf(ApiException.class).extracting("status.value").isEqualTo(409);
  when(users.findById(3L)).thenReturn(Optional.of(without)); when(processings.existsByUserIdAndStatusIn(eq(3L),any())).thenReturn(false);
  assertThatThrownBy(() -> service.find(3)).isInstanceOf(ApiException.class).extracting("status.value").isEqualTo(404);
 }
}
