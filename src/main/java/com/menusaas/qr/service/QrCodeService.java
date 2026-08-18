package com.menusaas.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Generación de códigos QR (PNG y PDF A4) apuntando a la URL pública del menú.
 */
@Service
public class QrCodeService {

    private static final int QR_SIZE = 512;

    public byte[] generatePng(String url) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el código QR", ex);
        }
    }

    public byte[] generatePdf(String url) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints());
            ByteArrayOutputStream qrPng = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", qrPng);

            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, pdf);
            document.open();
            Image image = Image.getInstance(qrPng.toByteArray());
            image.scaleToFit(400, 400);
            image.setAlignment(Image.ALIGN_CENTER);
            document.add(image);
            document.close();
            return pdf.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el PDF del código QR", ex);
        }
    }

    private Map<EncodeHintType, Object> hints() {
        return Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 1
        );
    }
}