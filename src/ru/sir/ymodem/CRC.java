package ru.sir.ymodem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by asirotinkin on 11.11.2014.
 */
public interface CRC {
    boolean readCRCAndCheck(InputStream inputStream, byte[] block) throws IOException;

    void writeCRC(OutputStream outputStream, byte[] block) throws IOException;
}
