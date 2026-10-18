package com.breathAI.ttobagi_server.domain.dashboard;

import com.breathAI.ttobagi_server.domain.dashboard.service.AnalysisUploadReader;
import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisUploadReaderTest {
    private final AnalysisUploadReader reader = new AnalysisUploadReader();

    @Test
    void csvRetainsOriginalBytesRegardlessOfBrowserMimeType() {
        byte[] content = "\ufeff질문,답변\r\n안녕,반가워\r\n".getBytes(StandardCharsets.UTF_8);
        var result = reader.read(new MockMultipartFile("file", "logs.CSV", "application/octet-stream", content));
        assertEquals("logs.CSV", result.filename());
        assertArrayEquals(content, result.content());
    }

    @Test
    void xlsxConvertsFirstSheetWithEscapingMissingCellsAndFormattedValues() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("로그");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("질문");
            header.createCell(1).setCellValue("공백");
            header.createCell(2).setCellValue("번호");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("한글, \"인용\"\n줄바꿈");
            var cell = row.createCell(2);
            cell.setCellValue(12);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("0000"));
            cell.setCellStyle(style);
            sheet.createRow(2);
            workbook.createSheet("무시").createRow(0).createCell(0).setCellValue("다른 시트");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        var result = reader.read(new MockMultipartFile("file", "logs.XLSX", "application/octet-stream", bytes));
        assertEquals("logs.csv", result.filename());
        try (var csv = CSVFormat.DEFAULT.parse(new StringReader(new String(result.content(), StandardCharsets.UTF_8)))) {
            var rows = csv.getRecords();
            assertEquals(2, rows.size());
            assertEquals("한글, \"인용\"\n줄바꿈", rows.get(1).get(0));
            assertEquals("", rows.get(1).get(1));
            assertEquals("0012", rows.get(1).get(2));
        }
    }

    @Test
    @DisplayName("엑셀 날짜 칸은 화면 서식과 상관없이 AI가 읽는 ISO 형식으로 바꾼다")
    void xlsxDatesBecomeIsoRegardlessOfDisplayFormat() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            String[] names = {"번호", "질문", "날짜", "날짜만", "숫자"};
            for (int i = 0; i < names.length; i++) {
                header.createCell(i).setCellValue(names[i]);
            }
            var format = workbook.createDataFormat();
            var dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(format.getFormat("m/d/yy h:mm"));
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(format.getFormat("m/d/yy"));
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("질문");
            var dateTime = row.createCell(2);
            dateTime.setCellValue(LocalDateTime.of(2026, 8, 27, 12, 4));
            dateTime.setCellStyle(dateTimeStyle);
            var dateOnly = row.createCell(3);
            dateOnly.setCellValue(LocalDateTime.of(2026, 8, 27, 0, 0));
            dateOnly.setCellStyle(dateStyle);
            row.createCell(4).setCellValue(3.5);
            workbook.write(output);
            bytes = output.toByteArray();
        }
        var result = reader.read(new MockMultipartFile("file", "logs.xlsx", null, bytes));
        try (var csv = CSVFormat.DEFAULT.parse(new StringReader(new String(result.content(), StandardCharsets.UTF_8)))) {
            var row = csv.getRecords().get(1);
            assertEquals("1", row.get(0));
            assertEquals("2026-08-27 12:04:00", row.get(2));
            assertEquals("2026-08-27", row.get(3));
            assertEquals("3.5", row.get(4));
        }
    }

    @Test
    void rejectsUnsupportedEmptyAndCorruptUploads() throws Exception {
        assertError("logs.xls", new byte[]{1}, ErrorCode.UNSUPPORTED_UPLOAD_FORMAT);
        assertError("logs.csv.exe", new byte[]{1}, ErrorCode.UNSUPPORTED_UPLOAD_FORMAT);
        assertError("logs.csv", new byte[0], ErrorCode.INVALID_UPLOAD_FILE);
        assertError("logs.xlsx", "not a workbook".getBytes(StandardCharsets.UTF_8), ErrorCode.INVALID_UPLOAD_FILE);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet();
            workbook.write(output);
            assertError("empty.xlsx", output.toByteArray(), ErrorCode.INVALID_UPLOAD_FILE);
        }
    }

    private void assertError(String filename, byte[] bytes, ErrorCode expected) {
        var exception = assertThrows(CustomException.class,
                () -> reader.read(new MockMultipartFile("file", filename, null, bytes)));
        assertEquals(expected, exception.getErrorCode());
    }
}
