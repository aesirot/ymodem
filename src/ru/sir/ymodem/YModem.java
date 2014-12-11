package ru.sir.ymodem;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

/**
 * YModem.<br/>
 * Block 0 contain minimal file information (only filename)<br/>
 * <p>
 * Created by Anton Sirotinkin (aesirot@mail.ru), Moscow 2014<br/>
 * I hope you will find this program useful.<br/>
 * You are free to use/modify the code for any purpose, but please leave a reference to me.<br/>
 */
public class YModem {
    private Modem modem;

    /**
     * Constructor
     *
     * @param inputStream  stream for reading received data from other side
     * @param outputStream stream for writing data to other side
     */
    public YModem(InputStream inputStream, OutputStream outputStream) {
        this.modem = new Modem(inputStream, outputStream);
    }

    /**
     * Send a file.<br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param file
     * @throws java.io.IOException
     */
    public void send(Path file) throws IOException {
        //check filename
        if (!file.getFileName().toString().matches("\\w{1,8}\\.\\w{1,3}")) {
            throw new IOException("Filename must be in DOS style (no spaces, max 8.3)");
        }

        //open file
        try (DataInputStream dataStream = new DataInputStream(Files.newInputStream(file))) {

            Timer timer = new Timer(Modem.WAIT_FOR_RECEIVER_TIMEOUT).start();
            boolean useCRC16 = modem.waitReceiverRequest(timer);
            CRC crc;
            if (useCRC16)
                crc = new CRC16();
            else
                crc = new CRC8();

            //send block 0
            BasicFileAttributes readAttributes = Files.readAttributes(file, BasicFileAttributes.class);
            String fileNameString = file.getFileName().toString() + (char)0 + ((Long) Files.size(file)).toString()+" "+ Long.toOctalString(readAttributes.lastModifiedTime().toMillis() / 1000);
            byte[] fileNameBytes = Arrays.copyOf(fileNameString.getBytes(), 128);
            modem.sendBlock(0, Arrays.copyOf(fileNameBytes, 128), 128, crc);

            modem.waitReceiverRequest(timer);
            //send data
            byte[] block = new byte[1024];
            modem.sendDataBlocks(dataStream, 1, crc, block);

            modem.sendEOT();
        }
    }

    /**
     * Send files in batch mode.<br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param files
     * @throws java.io.IOException
     */
    public void batchSend(Path... files) throws IOException {
        for (Path file : files) {
            send(file);
        }

        sendBatchStop();
    }

    private void sendBatchStop() throws IOException {
        Timer timer = new Timer(Modem.WAIT_FOR_RECEIVER_TIMEOUT).start();
        boolean useCRC16 = modem.waitReceiverRequest(timer);
        CRC crc;
        if (useCRC16)
            crc = new CRC16();
        else
            crc = new CRC8();

        //send block 0
        byte[] bytes = new byte[128];
        modem.sendBlock(0, bytes, bytes.length, crc);
    }

    /**
     * Receive single file <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param directory directory where file will be saved
     * @return path to created file
     * @throws java.io.IOException
     */
    public Path receiveSingleFileInDirectory(Path directory) throws IOException {
        return receive(directory, true);
    }

    /**
     * Receive files in batch mode <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param directory directory where files will be saved
     * @throws java.io.IOException
     */
    public void receiveFilesInDirectory(Path directory) throws IOException {
        while (receive(directory, true) != null) {
        }
    }

    /**
     * Receive path <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param path path to file where data will be saved
     * @return path to file
     * @throws java.io.IOException
     */
    public Path receive(Path path) throws IOException {
        return receive(path, false);
    }

    private Path receive(Path path, boolean inDirectory) throws IOException {
        DataOutputStream dataOutput = null;
        Path filePath;
        try {
            CRC crc = new CRC16();
            int errorCount = 0;

            // process block 0
            byte[] block;
            int character;
            while (true) {
                character = modem.requestTransmissionStart(true);
                try {
                    // read file name from zero block
                    block = modem.readBlock(0, (character == Modem.SOH), crc);

                    if (inDirectory) {
                        StringBuilder sb = new StringBuilder();
                        if (block[0] == 0) {
                            //this is stop block of batch file transfer
                            modem.sendByte(Modem.ACK);
                            return null;
                        }
                        for (int i = 0; i < block.length; i++) {
                            if (block[i] == 0) {
                                break;
                            }
                            sb.append((char) block[i]);
                        }
                        filePath = path.resolve(sb.toString());
                    } else {
                        filePath = path;
                    }
                    dataOutput = new DataOutputStream(Files.newOutputStream(filePath));
                    modem.sendByte(Modem.ACK);
                    break;
                } catch (TimeoutException | Modem.InvalidBlockException e) {
                    errorCount++;
                    if (errorCount == Modem.MAXERRORS) {
                        modem.interruptTransmission();
                        throw new IOException("Transmission aborted, error count exceeded max");
                    }
                    modem.sendByte(Modem.NAK);
                } catch (Modem.RepeatedBlockException | Modem.SynchronizationLostException e) {
                    //fatal transmission error
                    modem.interruptTransmission();
                    throw new IOException("Fatal transmission error", e);
                }
            }

            //receive data blocks
            modem.receive(filePath, true);
        } finally {
            if (dataOutput != null) {
                dataOutput.close();
            }
        }
        return filePath;
    }
}
