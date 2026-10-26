package uy.edu.um.nexo.mnatural.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

// Genera el código QR de una reserva confirmada (con ZXing). El QR contiene solo el código de reserva
// (por ejemplo MN-7KQ2XH4P): el prestador lo escanea o lo escribe a mano para verificar la reserva
@Service
public class BookingQrService {

    // Tamaño en píxeles: se lee bien desde el celular y pesa pocos KB
    public static final int SIZE = 300;

    public byte[] generatePng(String bookingCode) {
        if (bookingCode == null || bookingCode.isBlank()) {
            throw new IllegalArgumentException("Falta el código de reserva para generar el QR.");
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(bookingCode, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar el QR de la reserva " + bookingCode, e);
        }
    }
}
