package org.sysml.ast.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sysml.ast.model.ParseResult;
import org.sysml.ast.service.SysmlParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class CliRunner implements ApplicationRunner {

    @Autowired
    private SysmlParserService parserService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("cli")) {
            return;
        }

        String input = null;
        String filename = "stdin";

        if (args.containsOption("input")) {
            String inputArg = args.getOptionValues("input").get(0);
            File inputFile = new File(inputArg);
            if (inputFile.exists()) {
                input = Files.readString(inputFile.toPath());
                filename = inputFile.getName();
            } else {
                input = inputArg;
            }
        } else {
            input = new String(System.in.readAllBytes());
        }

        ParseResult result = parserService.parse(input, filename);
        String json = objectMapper.writeValueAsString(result);

        if (args.containsOption("output")) {
            String outputPath = args.getOptionValues("output").get(0);
            Files.writeString(Path.of(outputPath), json);
            System.out.println("Output written to: " + outputPath);
        } else {
            System.out.println(json);
        }

        System.exit(result.isSuccess() ? 0 : 1);
    }
}
