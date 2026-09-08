package com.example.photoapi.foto.web;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.foto.service.CurrentPhotoService;
import com.example.photoapi.foto.service.UploadPhotoService;
import com.example.photoapi.usuario.web.contract.ProcessingAcceptedResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/v1/usuarios/{usuarioId}")
public class PhotoController {
    private final CurrentPhotoService service;
    private final UploadPhotoService uploads;

    @Autowired
    public PhotoController(CurrentPhotoService service, UploadPhotoService uploads) {
        this.service = service;
        this.uploads = uploads;
    }

    PhotoController(CurrentPhotoService service) {
        this(service, null);
    }

    @GetMapping("/foto")
    ResponseEntity<byte[]> find(@PathVariable long usuarioId) {
        var photo = service.find(usuarioId);
        return ResponseEntity.ok().header("Content-Type", photo.contentType())
                .cacheControl(CacheControl.noStore()).body(photo.bytes());
    }

    @PostMapping(value = "/fotos", consumes = "multipart/form-data")
    ResponseEntity<ProcessingAcceptedResponse> upload(@PathVariable long usuarioId,
                                                        MultipartHttpServletRequest request) {
        List<MultipartFile> files = request.getMultiFileMap().values().stream()
                .flatMap(List::stream).toList();
        List<MultipartFile> photos = request.getFiles("foto");
        if (files.size() != 1 || photos.size() != 1 || files.getFirst() != photos.getFirst()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTIDADE_FOTOS_INVALIDA",
                    "O multipart deve conter exatamente uma parte de arquivo chamada foto");
        }
        return ResponseEntity.accepted().body(uploads.upload(usuarioId, photos.getFirst()));
    }
}
