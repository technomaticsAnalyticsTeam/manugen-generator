package ru.leyman.manugen.service;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileService {

    InputStream getFile(String filename) throws FileNotFoundException;

    OutputStream createFile(String filename) throws FileNotFoundException;

}
