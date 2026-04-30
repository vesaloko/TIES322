// code from 
public class CRC8 {

    private static final int POLYNOMIAL = 0x07;

    public static byte calculate(byte[] data, int length) {
        int crc = 0x00;

        for (int j = 0; j < length; j++) {
            crc ^= data[j] & 0xFF;

            for (int i = 0; i < 8; i++) {
                if ((crc & 0x80) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }

                crc &= 0xFF;
            }
        }

        return (byte) crc;
    }
}