package uy.edu.um.nexo.mnatural.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingQrServiceTest {

    private final BookingQrService service = new BookingQrService();

    @Test
    void theQrIsAPngThatContainsTheBookingCode() throws Exception {
        byte[] png = service.generatePng("MN-7KQ2XH4P");

        // Se vuelve a leer la imagen con ZXing, como haría el celular del prestador
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(BookingQrService.SIZE);
        String text = new MultiFormatReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))))
                .getText();
        assertThat(text).isEqualTo("MN-7KQ2XH4P");
    }

    @Test
    void withoutCodeThereIsNoQr() {
        assertThatThrownBy(() -> service.generatePng(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
