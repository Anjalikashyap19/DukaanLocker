package com.shoplocker.fssai.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders a QR code as a PNG data URI for embedding in generated certificates.
 *
 * <p>Uses only {@code com.google.zxing:core}: the {@link BitMatrix} returned by
 * {@link QRCodeWriter} is rasterised by hand into a {@link BufferedImage} and
 * written with {@code ImageIO}, which avoids pulling in the zxing {@code javase}
 * module. ZXing's writer already pads the matrix with the 4-module quiet zone the
 * QR spec requires, so scanners lock on at the small footer size.</p>
 */
public final class QrCodePng {

    private QrCodePng() {}

    /** Raster size handed to the writer — well above the ~110px the footer shows. */
    private static final int QR_PIXELS = 256;

    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;

    /**
     * Encodes {@code text} as a QR code and returns a {@code data:image/png;base64,…}
     * URI ready for an {@code <img src>}.
     *
     * @param text payload to encode, e.g. {@code "FSSAI:20421201000637"}
     * @return PNG data URI
     * @throws WriterException if the payload cannot be encoded (practically
     *                         unreachable for a short alphanumeric string)
     * @throws IOException      if the PNG cannot be written to memory
     */
    public static String pngDataUri(String text) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix matrix = new QRCodeWriter()
                .encode(text, BarcodeFormat.QR_CODE, QR_PIXELS, QR_PIXELS, hints);

        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
