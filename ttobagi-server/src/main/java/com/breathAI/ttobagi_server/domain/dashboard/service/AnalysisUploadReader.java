package com.breathAI.ttobagi_server.domain.dashboard.service;

import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/** Materializes the upload before asynchronous dispatch; XLSX uses its first sheet. */
@Component
public class AnalysisUploadReader {
    public record PipelineFile(String filename, byte[] content) {}

    public PipelineFile read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase(Locale.ROOT).endsWith(".csv")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"))) {
            throw new CustomException(ErrorCode.UNSUPPORTED_UPLOAD_FORMAT);
        }
        try {
            if (filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                // Preserve existing CSV encoding and contents for the AI CSV reader.
                return new PipelineFile(filename, file.getBytes());
            }
            try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
                if (workbook.getNumberOfSheets() == 0) {
                    throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
                }
                var sheet = workbook.getSheetAt(0);
                Row header = sheet.getRow(sheet.getFirstRowNum());
                if (header == null || header.getLastCellNum() <= 0) {
                    throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
                }
                int columns = header.getLastCellNum();
                var formatter = new DataFormatter(Locale.ROOT);
                var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                var output = new StringWriter();
                try (var csv = new CSVPrinter(output, CSVFormat.DEFAULT)) {
                    for (Row row : sheet) {
                        var values = new ArrayList<String>();
                        for (int column = 0; column < Math.max(columns, row.getLastCellNum()); column++) {
                            values.add(formatter.formatCellValue(row.getCell(column), evaluator));
                        }
                        if (values.stream().anyMatch(value -> !value.isBlank())) {
                            csv.printRecord(values);
                        }
                    }
                }
                if (output.toString().isBlank()) {
                    throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
                }
                return new PipelineFile(filename.substring(0, filename.length() - 5) + ".csv",
                        output.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }
    }
}
