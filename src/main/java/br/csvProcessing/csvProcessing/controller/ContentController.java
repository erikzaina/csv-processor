package br.csvProcessing.csvProcessing.controller;

import br.csvProcessing.csvProcessing.Service.CsvService;
import br.csvProcessing.csvProcessing.entity.CsvInfo;
import br.csvProcessing.csvProcessing.exception.InvalidCsvFormatException;
import br.csvProcessing.csvProcessing.repository.CsvRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
public class ContentController {

    private static final Logger logger = LoggerFactory.getLogger(ContentController.class);

    private final CsvService csvService;
    private final CsvRepository csvRepository;

    @Autowired
    public ContentController(CsvService csvService, CsvRepository csvRepository) {
        this.csvService = csvService;
        this.csvRepository = csvRepository;
    }

    @GetMapping("/")
    public String handleHome() {
        return "home";
    }

    @GetMapping("/upload")
    public String handleUpload() {
        return "upload";
    }

    @GetMapping("/error")
    public String handleError(Model model, @RequestParam(value = "message", required = false) String message) {
        model.addAttribute("message", message != null ? message : "Ocorreu um erro inesperado.");
        return "error";
    }

    @PostMapping("/upload")
    public String handleUploadPost(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            List<CsvInfo> csvList = csvService.processCsv(file);
            csvService.sortByNome(csvList);
            csvService.truncateAndSaveToDatabase(csvList);
            redirectAttributes.addFlashAttribute("successMessage", "Arquivo processado e salvo com sucesso!");
            return "redirect:/result";
        } catch (InvalidCsvFormatException e) {
            logger.warn("CSV processing error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/upload";
        } catch (Exception e) {
            logger.error("Unexpected error during CSV upload: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Erro inesperado ao processar o arquivo. Por favor, tente novamente.");
            return "redirect:/error";
        }
    }

    @GetMapping("/login")
    public String handleLogin() {
        return "login";
    }

    @GetMapping("/result")
    public String handleResult(Model model) {
        try {
            List<CsvInfo> dados = csvRepository.findAll();
            dados = csvService.sortByNome(dados);
            Map<String, Object> statistics = csvService.calculateStatistics(dados);
            model.addAttribute("dados", dados);
            model.addAttribute("maleCount", statistics.get("maleCount"));
            model.addAttribute("femaleCount", statistics.get("femaleCount"));
            model.addAttribute("maleAverageAge", String.format("%.1f", statistics.get("maleAverageAge")));
            model.addAttribute("femaleAverageAge", String.format("%.1f", statistics.get("femaleAverageAge")));
            return "result";
        } catch (Exception e) {
            logger.error("Erro ao carregar os resultados: {}", e.getMessage(), e);
            model.addAttribute("message", "Erro ao carregar os resultados: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) throws IOException {
        try {
            List<CsvInfo> dados = csvRepository.findAll();
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exported_data.csv\"");
            String csvContent = csvService.exportCsv(dados);
            response.getWriter().write(csvContent);
        } catch (Exception e) {
            logger.error("Erro ao exportar o CSV: {}", e.getMessage(), e);
            throw new IOException("Erro ao exportar o CSV: " + e.getMessage());
        }
    }
}