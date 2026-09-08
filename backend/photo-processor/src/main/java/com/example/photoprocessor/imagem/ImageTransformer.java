package com.example.photoprocessor.imagem;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;

public class ImageTransformer {
    private final InputPixelGuard pixelGuard;
    public ImageTransformer(InputPixelGuard pixelGuard) { this.pixelGuard = pixelGuard; }

    public TransformedImage transform(byte[] original) {
        pixelGuard.inspect(original);
        Format format = detect(original);
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(original));
            if (decoded == null) throw invalid();
            BufferedImage oriented = applyExifOrientation(decoded, format.extension.equals("jpg") ? exifOrientation(original) : 1);
            double ratio = Math.min(1d, Math.min(1024d / oriented.getWidth(), 1024d / oriented.getHeight()));
            int targetWidth = Math.max(1, (int) Math.round(oriented.getWidth() * ratio));
            int targetHeight = Math.max(1, (int) Math.round(oriented.getHeight() * ratio));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            var builder = Thumbnails.of(oriented)
                    .size(targetWidth, targetHeight).keepAspectRatio(true)
                    .outputFormat(format.extension);
            if (format.extension.equals("jpg")) builder.outputQuality(0.85);
            builder.toOutputStream(output);
            byte[] bytes = output.toByteArray();
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(bytes));
            return new TransformedImage(bytes, format.contentType, format.extension,
                    result.getWidth(), result.getHeight(), checksum(bytes));
        } catch (FunctionalProcessingException exception) { throw exception; }
        catch (Exception exception) { throw new FunctionalProcessingException("INVALID_IMAGE_CONTENT", "Falha ao transformar imagem", exception); }
    }

    private Format detect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255)
            return new Format("image/jpeg", "jpg");
        if (bytes.length >= 8 && (bytes[0]&255)==137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71)
            return new Format("image/png", "png");
        throw invalid();
    }
    private FunctionalProcessingException invalid() { return new FunctionalProcessingException("INVALID_IMAGE_CONTENT", "Formato não suportado"); }
    private int exifOrientation(byte[] jpeg) {
        for (int offset = 2; offset + 4 < jpeg.length && (jpeg[offset] & 0xff) == 0xff;) {
            int marker = jpeg[offset + 1] & 0xff;
            if (marker == 0xda || marker == 0xd9) break;
            int length = ((jpeg[offset + 2] & 0xff) << 8) | (jpeg[offset + 3] & 0xff);
            if (length < 2 || offset + 2 + length > jpeg.length) break;
            if (marker == 0xe1 && length >= 10 && jpeg[offset + 4] == 'E' && jpeg[offset + 5] == 'x') {
                int tiff = offset + 10;
                boolean little = jpeg[tiff] == 'I' && jpeg[tiff + 1] == 'I';
                int ifd = tiff + readInt(jpeg, tiff + 4, little);
                int count = readShort(jpeg, ifd, little);
                for (int i = 0; i < count; i++) {
                    int entry = ifd + 2 + i * 12;
                    if (entry + 11 >= offset + 2 + length) break;
                    if (readShort(jpeg, entry, little) == 0x0112) return readShort(jpeg, entry + 8, little);
                }
            }
            offset += 2 + length;
        }
        return 1;
    }
    private int readShort(byte[] bytes, int offset, boolean little) {
        return little ? (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                : ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }
    private int readInt(byte[] bytes, int offset, boolean little) {
        return little ? readShort(bytes, offset, true) | (readShort(bytes, offset + 2, true) << 16)
                : (readShort(bytes, offset, false) << 16) | readShort(bytes, offset + 2, false);
    }
    private BufferedImage applyExifOrientation(BufferedImage source, int orientation) {
        if (orientation < 2 || orientation > 8) return source;
        int width = source.getWidth(), height = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? height : width, swap ? width : height,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, transform, null); graphics.dispose();
        return target;
    }
    private String checksum(byte[] bytes) throws Exception { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private record Format(String contentType, String extension) {}
}
