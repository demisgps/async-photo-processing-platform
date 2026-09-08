package com.example.photoconsumer.processamento.service;

import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import org.springframework.stereotype.Service;

@Service
public class EventDecisionService {
    public enum Decision { PROCESS, RESUME, ACK_NO_OP }

    public Decision forResult(ProcessingStatus status) {
        return switch (status) {
            case PROCESSANDO -> Decision.PROCESS;
            case PERSISTINDO -> Decision.RESUME;
            default -> Decision.ACK_NO_OP;
        };
    }

    public Decision forError(ProcessingStatus status) {
        return status == ProcessingStatus.PROCESSANDO ? Decision.PROCESS : Decision.ACK_NO_OP;
    }
}
