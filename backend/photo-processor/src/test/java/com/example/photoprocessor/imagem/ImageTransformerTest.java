package com.example.photoprocessor.imagem;

import static org.assertj.core.api.Assertions.*;
import com.example.photoprocessor.TestImages;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageTransformerTest {
    ImageTransformer transformer=new ImageTransformer(new InputPixelGuard(25_000_000));
    @Test void boundsJpegPreservesRatioAndDoesNotUpscale() throws Exception {
        var large=transformer.transform(TestImages.image("jpg",1600,800,false)); assertThat(large.width()).isEqualTo(1024); assertThat(large.height()).isEqualTo(512);
        var small=transformer.transform(TestImages.image("jpg",100,50,false)); assertThat(small.width()).isEqualTo(100); assertThat(small.height()).isEqualTo(50);
    }
    @Test void preservesPngAlpha() throws Exception {
        var result=transformer.transform(TestImages.image("png",30,20,true)); var decoded=ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(result.contentType()).isEqualTo("image/png"); assertThat(decoded.getColorModel().hasAlpha()).isTrue();
    }
    @Test void correctsExifOrientation() throws Exception {
        var result=transformer.transform(TestImages.jpegWithOrientation6(40,20)); assertThat(result.width()).isEqualTo(20); assertThat(result.height()).isEqualTo(40);
    }
    @Test void rejectsInvalidImage() { assertThatThrownBy(() -> transformer.transform(new byte[]{1,2,3})).isInstanceOf(FunctionalProcessingException.class); }
}
