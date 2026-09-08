package com.example.photoapi.foto.validation;

import com.example.photoapi.config.UploadProperties;
import com.example.photoapi.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PhotoValidator {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private final UploadProperties properties;

    public PhotoValidator(UploadProperties properties) { this.properties = properties; }

    public ValidatedPhoto validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("FOTO_OBRIGATORIA", "Exatamente uma foto é obrigatória");
        }
        if (file.getSize() > properties.maxBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "ARQUIVO_MUITO_GRANDE",
                    "A foto deve ter no máximo 10 MiB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FALHA_LEITURA_FOTO",
                    "Não foi possível ler a foto");
        }
        Format format = detect(bytes);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                throw unsupported();
            }
        } catch (IOException | RuntimeException exception) {
            throw unsupported();
        }
        return new ValidatedPhoto(bytes, format.contentType, format.extension);
    }

    private Format detect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return new Format("image/jpeg", "jpg");
        }
        if (bytes.length >= PNG.length) {
            for (int i = 0; i < PNG.length; i++) {
                if (bytes[i] != PNG[i]) throw unsupported();
            }
            return new Format("image/png", "png");
        }
        throw unsupported();
    }

    private ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FORMATO_NAO_SUPORTADO",
                "A foto deve ser um JPG/JPEG ou PNG válido");
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record Format(String contentType, String extension) {}
}
