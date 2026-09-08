package com.example.photoprocessor;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

public final class TestImages {
    private TestImages() {}
    public static byte[] image(String format, int width, int height, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(alpha ? new Color(255, 0, 0, 80) : Color.RED);
        graphics.fillRect(0, 0, width, height); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, format, output); return output.toByteArray();
    }
    public static byte[] jpegWithOrientation6(int width, int height) throws Exception {
        byte[] jpeg = image("jpg", width, height, false);
        byte[] exif = {(byte)0xff,(byte)0xe1,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,
                1,0,0x12,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};
        byte[] result = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg,0,result,0,2); System.arraycopy(exif,0,result,2,exif.length);
        System.arraycopy(jpeg,2,result,2+exif.length,jpeg.length-2); return result;
    }
}
