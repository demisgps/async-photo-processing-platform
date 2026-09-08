package com.example.photoapi.usuario.web;

import com.example.photoapi.exception.ApiException;
import com.example.photoapi.usuario.service.CreateUsuarioService;
import com.example.photoapi.usuario.service.UpdateUsuarioService;
import com.example.photoapi.usuario.service.UsuarioQueryService;
import com.example.photoapi.usuario.service.DeleteUsuarioService;
import com.example.photoapi.usuario.web.contract.UpdateUserNameRequest;
import com.example.photoapi.usuario.web.contract.UserDetailResponse;
import com.example.photoapi.usuario.web.contract.UserSummaryResponse;
import com.example.photoapi.usuario.web.contract.UserCreatedResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
    private final CreateUsuarioService service;
    private final UsuarioQueryService queries;
    private final UpdateUsuarioService updates;
    private final DeleteUsuarioService deletions;

    public UsuarioController(CreateUsuarioService service, UsuarioQueryService queries, UpdateUsuarioService updates,
                             DeleteUsuarioService deletions) {
        this.service = service; this.queries = queries; this.updates = updates; this.deletions = deletions;
    }

    UsuarioController(CreateUsuarioService service) { this(service, null, null, null); }

    @GetMapping
    List<UserSummaryResponse> list() { return queries.list(); }

    @GetMapping("/{usuarioId}")
    UserDetailResponse find(@PathVariable long usuarioId) { return queries.find(usuarioId); }

    @PutMapping("/{usuarioId}")
    UserDetailResponse update(@PathVariable long usuarioId, @RequestBody UpdateUserNameRequest request) {
        return updates.update(usuarioId, request.nome());
    }

    @DeleteMapping("/{usuarioId}")
    ResponseEntity<Void> delete(@PathVariable long usuarioId) {
        deletions.delete(usuarioId);
        return ResponseEntity.noContent().build();
    }

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
