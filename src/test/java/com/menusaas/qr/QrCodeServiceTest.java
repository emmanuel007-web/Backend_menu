package com.menusaas.qr;

import com.menusaas.qr.service.QrCodeService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    void generatesValidPng() {
        byte[] png = qrCodeService.generatePng("https://menu.example.com/fritomix");

        assertThat(png).isNotEmpty();
        assertThat(new String(png, 0, 4, StandardCharsets.ISO_8859_1))
                .isEqualTo("\u0089PNG");
    }

    @Test
    void generatesValidPdf() {
        byte[] pdf = qrCodeService.generatePdf("https://menu.example.com/fritomix");

        assertThat(pdf).isNotEmpty();
        assertThat(pdf).startsWith(new byte[]{0x25, 0x50, 0x44, 0x46}); // %PDF
    }
}