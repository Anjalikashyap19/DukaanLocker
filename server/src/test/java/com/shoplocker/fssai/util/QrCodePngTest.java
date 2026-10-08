package com.shoplocker.fssai.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The footer QR is the only machine-readable part of the certificate, so it is
 * checked by actually decoding it back — zxing's {@code RGBLuminanceSource}
 * lives in the core artifact, which keeps the test free of the javase module.
 */
public class QrCodePngTest {

    private static final String PREFIX = "data:image/png;base64,";
    private static final String PAYLOAD = "FSSAI:20421201000637";

    private BufferedImage pngOf(String dataUri) throws Exception {
        assertTrue(dataUri.startsWith(PREFIX), "must be a PNG data URI");
        byte[] png = Base64.getDecoder().decode(dataUri.substring(PREFIX.length()));
        assertEquals((byte) 0x89, png[0], "PNG magic byte 0");
        assertEquals((byte) 'P', png[1], "PNG magic byte 1");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(image != null && image.getWidth() > 0, "PNG must decode");
        return image;
    }

    @Test
    void emitsADecodablePngDataUri() throws Exception {
        assertTrue(pngOf(QrCodePng.pngDataUri(PAYLOAD)).getWidth() >= 100);
    }

    @Test
    void theEncodedPayloadSurvivesARealScan() throws Exception {
        BufferedImage image = pngOf(QrCodePng.pngDataUri(PAYLOAD));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));

        assertEquals(PAYLOAD, new QRCodeReader().decode(bitmap).getText());
    }

    @Test
    void theSameLicenceAlwaysRendersTheSameImage() throws Exception {
        assertEquals(QrCodePng.pngDataUri(PAYLOAD), QrCodePng.pngDataUri(PAYLOAD));
    }

    @Test
    void aDifferentLicenceRendersADifferentImage() throws Exception {
        assertNotEquals(QrCodePng.pngDataUri(PAYLOAD),
                QrCodePng.pngDataUri("FSSAI:20421201000638"));
    }
}
