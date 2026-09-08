package com.example.photoapi.usuario.web;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.usuario.service.CreateUsuarioService;
import com.example.photoapi.usuario.web.contract.UserCreatedResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
    private final CreateUsuarioService service;

    public UsuarioController(CreateUsuarioService service) { this.service = service; }

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<UserCreatedResponse> create(@RequestParam("nome") String name,
                                                MultipartHttpServletRequest request) {
        List<MultipartFile> files = request.getMultiFileMap().values().stream().flatMap(List::stream).toList();
        List<MultipartFile> photos = request.getFiles("foto");
        if (files.size() != 1 || photos.size() != 1 || files.getFirst() != photos.getFirst()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTIDADE_FOTOS_INVALIDA",
                    "O multipart deve conter exatamente uma parte de arquivo chamada foto");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(name, photos.getFirst()));
    }
}
