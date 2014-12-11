package ru.sir.ymodem;

/**
 * Created by asirotinkin on 11.11.2014.
 */
public class CRC8 implements CRC {
    @Override
    public int getCRCLength() {
        return 1;
    }

    @Override
    public long calcCRC(byte[] block) {
        byte checkSumma = 0;
        for (int i = 0; i < block.length; i++) {
            checkSumma += block[i];
        }
        return checkSumma;
    }

}
