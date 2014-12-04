package ru.sir.ymodem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by asirotinkin on 11.11.2014.
 */
public class CRC8 implements CRC {
    @Override
    public boolean readCRCAndCheck(InputStream inputStream, byte[] block) throws IOException {
        byte checkSumma = (byte)inputStream.read();
        return (checkSumma == calcCRC8(block));
    }

    @Override
    public void writeCRC(OutputStream outputStream, byte[] block) throws IOException {
        outputStream.write(calcCRC8(block));
    }

    private byte calcCRC8(byte[] block) {
        byte checkSumma = 0;
        for (int i = 0; i < block.length; i++) {
            checkSumma += block[i];
        }
        return checkSumma;
    }

}
