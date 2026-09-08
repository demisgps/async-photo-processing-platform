package com.example.photoprocessor.imagem;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class InputPixelGuard {
    private final long maxPixels;
    public InputPixelGuard(long maxPixels) {
        if (maxPixels < 1) throw new IllegalArgumentException("maxPixels deve ser positivo");
        this.maxPixels = maxPixels;
    }

    public Dimension inspect(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Imagem JPG/PNG inválida");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validate(width, height);
                return new Dimension(width, height);
            } finally { reader.dispose(); }
        } catch (FunctionalProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FunctionalProcessingException("INVALID_IMAGE_CONTENT", "Imagem não pôde ser lida", exception);
        }
    }

    public void validate(long width, long height) {
        if (width < 1 || height < 1 || width > maxPixels / height) {
            throw invalid("Imagem excede o limite de " + maxPixels + " pixels");
        }
    }

    private FunctionalProcessingException invalid(String message) {
        return new FunctionalProcessingException("INVALID_IMAGE_CONTENT", message);
    }
}
