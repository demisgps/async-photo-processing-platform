package com.example.photoapi.processamento.web;

import com.example.photoapi.processamento.service.ProcessingQueryService;
import com.example.photoapi.usuario.web.contract.ProcessingResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/processamentos")
public class ProcessingController {
    private final ProcessingQueryService service;
    public ProcessingController(ProcessingQueryService service) { this.service = service; }
    @GetMapping("/{processamentoId}")
    ProcessingResponse find(@PathVariable UUID processamentoId) { return service.find(processamentoId); }
}
