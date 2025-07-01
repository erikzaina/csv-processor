package br.csvProcessing.csvProcessing.service;

import br.csvProcessing.csvProcessing.entity.CsvInfo;
import br.csvProcessing.csvProcessing.entity.GENERO;
import br.csvProcessing.csvProcessing.exception.InvalidCsvFormatException;
import br.csvProcessing.csvProcessing.repository.CsvRepository;
import com.opencsv.*;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class CsvService {

    private static final Logger logger = LoggerFactory.getLogger(CsvService.class);
    private static final String EXPECTED_HEADER = "Nome,SobreNome,Email,Sexo,IpAcesso,Idade,Nascimento";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final CsvRepository csvRepository;

    public CsvService(CsvRepository csvRepository) {
        this.csvRepository = csvRepository;
    }

    public List<CsvInfo> processCsv(MultipartFile file) throws IOException {
        validateCsvFile(file);

        List<CsvInfo> csvList = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                .withCSVParser(new CSVParserBuilder()
                        .withSeparator(',')
                        .withQuoteChar('"')
                        .withIgnoreQuotations(true)
                        .build())
                .build()) {
            String[] line;
            reader.readNext();
            int lineNumber = 1;
            while ((line = reader.readNext()) != null) {
                lineNumber++;
                try {
                    if (line.length < 7) {
                        continue;
                    }
                    csvList.add(parseCsvLine(line, lineNumber));
                } catch (InvalidCsvFormatException e) {
                    throw e;
                }
            }
            return csvList;
        } catch (CsvValidationException e) {
            logger.error("CSV validation error: {}", e.getMessage());
            throw new InvalidCsvFormatException("Erro ao validar o CSV: " + e.getMessage());
        }
    }

    private CsvInfo parseCsvLine(String[] line, int lineNumber) {
        CsvInfo csvLine = new CsvInfo();
        try {
            csvLine.setNome(line[0].trim());
            csvLine.setSobrenome(line[1].trim());
            csvLine.setEmail(line[2].trim());
            csvLine.setSexo(parseGender(line[3].trim(), lineNumber));
            csvLine.setIpAcesso(line[4].trim());
            csvLine.setIdade(parseAge(line[5].trim(), lineNumber));
            csvLine.setNascimento(parseAndCorrectDate(line[6].trim(), csvLine.getIdade(), lineNumber));
            return csvLine;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCsvFormatException("Erro na linha " + lineNumber + ": " + e.getMessage());
        }
    }

    private GENERO parseGender(String gender, int lineNumber) {
        try {
            return GENERO.valueOf(gender.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidCsvFormatException("Sexo inválido na linha " + lineNumber + ": " + gender);
        }
    }

    private Integer parseAge(String age, int lineNumber) {
        try {
            return Integer.parseInt(age.trim());
        } catch (NumberFormatException e) {
            throw new InvalidCsvFormatException("Idade inválida na linha " + lineNumber + ": " + age);
        }
    }

    private LocalDate parseAndCorrectDate(String dateStr, Integer age, int lineNumber) {
        try {
            int firstSlash = dateStr.indexOf("/");
            int secondSlash = dateStr.indexOf("/", firstSlash + 1);
            String dayMonth = dateStr.substring(0, secondSlash + 1);
            LocalDate today = LocalDate.now();
            int estimatedYear = today.getYear() - age;
            String correctedDateStr = dayMonth + estimatedYear;
            LocalDate birthDate = LocalDate.parse(correctedDateStr, DATE_FORMATTER);
            int day = birthDate.getDayOfMonth();
            int month = birthDate.getMonthValue();
            LocalDate thisYearBirthday = LocalDate.of(today.getYear(), month, day);
            if (thisYearBirthday.isAfter(today)) {
                birthDate = birthDate.minusYears(1);
            }
            return birthDate;
        } catch (DateTimeParseException e) {
            throw new InvalidCsvFormatException("Data de nascimento inválida na linha " + lineNumber + ": " + dateStr);
        }
    }

    public List<CsvInfo> sortByNome(List<CsvInfo> csvList) {
        csvList.sort(Comparator
                .comparing(CsvInfo::getNome, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CsvInfo::getSobrenome, String.CASE_INSENSITIVE_ORDER));
        return csvList;
    }

    public Map<String, Object> calculateStatistics(List<CsvInfo> csvList) {
        long maleCount = csvList.stream().filter(p -> p.getSexo() == GENERO.MALE).count();
        long femaleCount = csvList.stream().filter(p -> p.getSexo() == GENERO.FEMALE).count();

        double maleAverageAge = csvList.stream()
                .filter(p -> p.getSexo() == GENERO.MALE)
                .mapToInt(CsvInfo::getIdade)
                .average()
                .orElse(0.0);
        double femaleAverageAge = csvList.stream()
                .filter(p -> p.getSexo() == GENERO.FEMALE)
                .mapToInt(CsvInfo::getIdade)
                .average()
                .orElse(0.0);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("maleCount", maleCount);
        statistics.put("femaleCount", femaleCount);
        statistics.put("maleAverageAge", maleAverageAge);
        statistics.put("femaleAverageAge", femaleAverageAge);
        return statistics;
    }

    public void truncateAndSaveToDatabase(List<CsvInfo> csvList) {
        try {
            csvRepository.truncateTable();
            csvRepository.saveAll(csvList);
        } catch (Exception e) {
            logger.error("Erro ao salvar dados no banco de dados: {}", e.getMessage());
            throw new RuntimeException("Erro ao salvar dados no banco de dados: " + e.getMessage());
        }
    }

    private void validateCsvFile(MultipartFile file) {
        if (file.isEmpty()) {
            logger.error("O arquivo enviado está vazio.");
            throw new InvalidCsvFormatException("O arquivo enviado está vazio.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            logger.error("O arquivo deve ter a extensão .csv {}", originalFilename);
            throw new InvalidCsvFormatException("O arquivo deve ter a extensão .csv.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("text/csv") && !contentType.equals("application/vnd.ms-excel")) {
            logger.error("O arquivo deve ser do tipo CSV (text/csv). {}", contentType);
            throw new InvalidCsvFormatException("O arquivo deve ser do tipo CSV (text/csv).");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String actualHeader = reader.readLine();
            if (actualHeader == null) {
                logger.error("O arquivo CSV está vazio ou corrompido.");
                throw new InvalidCsvFormatException("O arquivo CSV está vazio ou corrompido.");
            }

            String normalizedActual = actualHeader.replaceAll("[^\\p{Print}]", "")
                    .replaceAll("\\s+", "")
                    .replace("\"", "")
                    .toLowerCase();

            String normalizedExpected = EXPECTED_HEADER
                    .replaceAll("[^\\p{Print}]", "")
                    .replaceAll("\\s+", "")
                    .replace("\"", "")
                    .toLowerCase();
            if (!normalizedActual.equals(normalizedExpected)) {
                logger.error("Formato de CSV inválido. '{}', O cabeçalho esperado é '{}'", EXPECTED_HEADER, actualHeader);
                throw new InvalidCsvFormatException("Formato de CSV inválido. O cabeçalho esperado é '" + EXPECTED_HEADER + "'.");
            }

        } catch (IOException e) {
            logger.error("Erro ao ler o arquivo CSV: {}", e.getMessage());
            throw new InvalidCsvFormatException("Erro ao ler o arquivo CSV: " + e.getMessage());
        }
    }

    public String exportCsv(List<CsvInfo> csvList) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (ICSVWriter csvWriter = new CSVWriterBuilder(stringWriter)
                .withQuoteChar(CSVWriter.NO_QUOTE_CHARACTER)
                .build()) {
            csvWriter.writeNext(new String[]{"Nome", "SobreNome", "Email", "Sexo", "IpAcesso", "Idade", "Nascimento"});
            for (CsvInfo csvInfo : csvList) {
                csvWriter.writeNext(new String[]{
                        csvInfo.getNome(),
                        csvInfo.getSobrenome(),
                        csvInfo.getEmail(),
                        csvInfo.getSexo().toString(),
                        csvInfo.getIpAcesso(),
                        String.valueOf(csvInfo.getIdade()),
                        csvInfo.getNascimento() != null ? csvInfo.getNascimento().format(DATE_FORMATTER) : ""
                });
            }
        }
        return stringWriter.toString();
    }
}