package ru.leyman.manugen.generator.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;

@Log4j2
@Service
@ConditionalOnProperty(name = "files.mode", havingValue = "in-memory")
public class InMemoryFileService implements FileService {

    @Override
    public InputStream getFile(String filename) throws FileNotFoundException {
        return new FileInputStream(filename);
    }

    @Override
    public OutputStream createFile(String filename) throws FileNotFoundException {
        return new FileOutputStream(filename);
    }

}
