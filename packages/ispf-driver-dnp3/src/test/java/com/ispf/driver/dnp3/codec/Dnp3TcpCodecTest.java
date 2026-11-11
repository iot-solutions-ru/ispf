package com.ispf.driver.dnp3.codec;

import com.ispf.driver.DriverException;
import com.ispf.driver.dnp3.Dnp3Point;
import com.ispf.driver.dnp3.codec.Dnp3TcpCodec.ControlCommand;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Dnp3TcpCodecTest {

    @Test
    void resetLinkFrameMatchesHandwrittenOctets() {
        byte[] expected = new byte[] {
                0x05, 0x64, 0x05, (byte) 0xC0, 0x01, 0x00, 0x00, 0x00, (byte) 0x91, (byte) 0xF8
        };
        assertArrayEquals(expected, Dnp3TcpCodec.resetLinkRequest(1, 0));
    }

    @Test
    void crc16DnpCatalogueAndHeader() {
        assertEquals(0xEA82, independentBitLoopCrc("123456789".getBytes(StandardCharsets.US_ASCII)));
        byte[] header = new byte[] {
                0x05, 0x64, 0x05, (byte) 0xC0, 0x01, 0x00, 0x00, 0x00
        };
        assertEquals(0xF891, Dnp3TcpCodec.crc16(header));
    }

    @Test
    void directOperateRequestsUseMasterDirectionAndObjectBody() throws Exception {
        byte[] crobOn = Dnp3TcpCodec.directOperateRequest(1, 1024, 0,
                new ControlCommand(Dnp3Point.Dnp3DataType.BINARY_OUTPUT, 0, true));
        assertEquals(0xC4, Byte.toUnsignedInt(crobOn[3]));
        Dnp3TcpCodec.Frame onFrame = Dnp3TcpCodec.readFrame(new ByteArrayInputStream(crobOn));
        assertArrayEquals(new byte[] {
                (byte) 0xC0, 0x05,
                0x0C, 0x01, 0x28, 0x01, 0x00, 0x00, 0x00,
                0x03, 0x01,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00
        }, onFrame.application());

        byte[] crobOff = Dnp3TcpCodec.directOperateRequest(1, 1024, 0,
                new ControlCommand(Dnp3Point.Dnp3DataType.BINARY_OUTPUT, 0, false));
        assertEquals(0x04, Dnp3TcpCodec.readFrame(new ByteArrayInputStream(crobOff)).application()[9]);

        byte[] analog = Dnp3TcpCodec.directOperateRequest(1, 1024, 0,
                new ControlCommand(Dnp3Point.Dnp3DataType.ANALOG_OUTPUT, 0, 7.25));
        assertEquals(0xC4, Byte.toUnsignedInt(analog[3]));
        assertArrayEquals(new byte[] {
                (byte) 0xC0, 0x05,
                0x29, 0x03, 0x28, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, (byte) 0xE8, 0x40,
                0x00
        }, Dnp3TcpCodec.readFrame(new ByteArrayInputStream(analog)).application());

        byte[] response = Dnp3TcpCodec.directOperateResponse(1024, 1,
                new ControlCommand(Dnp3Point.Dnp3DataType.BINARY_OUTPUT, 0, true), 0);
        assertEquals(0x44, Byte.toUnsignedInt(response[3]));
    }

    @Test
    void operateResponseWithRequestErrorIndicationIsRejected() {
        byte[] application = {(byte) 0xC0, (byte) 0x81, 0x00, 0x02};
        byte[] wire = unconfirmedFrame(0x44, 1, 1024, application);
        DriverException error = assertThrows(DriverException.class, () ->
                Dnp3TcpCodec.applyOperateResponse(Dnp3TcpCodec.readFrame(new ByteArrayInputStream(wire))));
        assertEquals(true, error.getMessage().contains("IIN2=0x2"));
    }

    private static byte[] unconfirmedFrame(int control, int destination, int source, byte[] application) {
        byte[] user = new byte[1 + application.length];
        user[0] = (byte) 0xC0;
        System.arraycopy(application, 0, user, 1, application.length);
        byte[] header = {
                0x05, 0x64, (byte) (5 + user.length), (byte) control,
                (byte) (destination & 0xFF), (byte) ((destination >>> 8) & 0xFF),
                (byte) (source & 0xFF), (byte) ((source >>> 8) & 0xFF)
        };
        byte[] headerCrc = crcLe(Dnp3TcpCodec.crc16(header));
        byte[] userCrc = crcLe(Dnp3TcpCodec.crc16(user));
        byte[] frame = new byte[header.length + headerCrc.length + user.length + userCrc.length];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(headerCrc, 0, frame, header.length, headerCrc.length);
        System.arraycopy(user, 0, frame, header.length + headerCrc.length, user.length);
        System.arraycopy(userCrc, 0, frame, header.length + headerCrc.length + user.length, userCrc.length);
        return frame;
    }

    private static byte[] crcLe(int crc) {
        return new byte[] {(byte) (crc & 0xFF), (byte) ((crc >>> 8) & 0xFF)};
    }

    /** Independent CRC-16/DNP bit loop (reflected poly 0xA6BC, init 0, xorout 0xFFFF). */
    private static int independentBitLoopCrc(byte[] data) {
        int crc = 0;
        for (byte datum : data) {
            crc ^= Byte.toUnsignedInt(datum);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA6BC;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return (crc ^ 0xFFFF) & 0xFFFF;
    }
}
