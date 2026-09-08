package com.example.photoapi.foto.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.photoapi.config.UploadProperties;
import com.example.photoapi.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PhotoValidatorTest {
    private static final int MAX = 10_485_760;
    private final PhotoValidator validator = new PhotoValidator(new UploadProperties(MAX));

    @Test void detectsJpegAndPngByContentAndNormalizesExtension() throws Exception {
        var jpeg = validator.validate(file("enganoso.png", image("jpg")));
        var png = validator.validate(file("sem-extensao", image("png")));
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.extension()).isEqualTo("jpg");
        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.extension()).isEqualTo("png");
    }

    @Test void acceptsValidImageExactlyAtLimit() throws Exception {
        byte[] base = image("png");
        byte[] exact = Arrays.copyOf(base, MAX);
        assertThat(validator.validate(file("x.png", exact)).bytes()).hasSize(MAX);
    }

    @Test void rejectsOneByteOverLimitBeforeDecode() {
        assertThatThrownBy(() -> validator.validate(file("x.png", new byte[MAX + 1])))
                .isInstanceOf(ApiException.class).extracting("status.value").isEqualTo(413);
    }

    @Test void rejectsUnknownMagicEmptyAndCorruptPayloadWithValidMagic() {
        assertThatThrownBy(() -> validator.validate(file("x", new byte[]{1, 2, 3}))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(file("x", new byte[0]))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(file("x.jpg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1})))
                .isInstanceOf(ApiException.class);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("foto", name, "application/octet-stream", bytes);
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
